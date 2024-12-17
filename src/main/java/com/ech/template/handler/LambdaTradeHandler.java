package com.ech.template.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.ech.template.model.Balance;
import com.ech.template.model.BalanceResponse;
import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.module.CommonModule;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.MetricsService;
import com.ech.template.service.OperationService;
import com.ech.template.service.PriceDiffService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.utils.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ech.template.service.BinanceClient.USDT_COIN_NAME;

@Log4j2
@AllArgsConstructor
public class LambdaTradeHandler implements RequestHandler<LambdaTradeHandler.LambdaInput, Void> {

    public static class LambdaInput {}

    private final BinanceClient client;
    private final DynamoDbService dynamoDbService;
    private final OperationService operationService;
    private final MetricsService metricsService;
    private final PriceDiffService priceDiffService;

    public LambdaTradeHandler() {
        Injector injector = Guice.createInjector(new CommonModule());
        this.client = injector.getInstance(BinanceClient.class);
        this.dynamoDbService = injector.getInstance(DynamoDbService.class);
        this.operationService = injector.getInstance(OperationService.class);
        this.metricsService = injector.getInstance(MetricsService.class);
        this.priceDiffService = injector.getInstance(PriceDiffService.class);
    }

    public static void main(String[] args) {
        new LambdaTradeHandler().handleRequest(new LambdaInput(), null);
    }

    @Override
    public Void handleRequest(LambdaInput lambdaInput, Context context) {

        List<WalletCoin> coinsFromDynamo = dynamoDbService.loadDynamoWallet();
        List<Balance> balanceCoins;
        if (CollectionUtils.isNullOrEmpty(coinsFromDynamo)) {
            log.info("Load coins from API");
            List<BalanceResponse.SnapshotVos.Data.Balance> walletCoins = client.getCurrentBalanceCoins();
            walletCoins.forEach(coin -> dynamoDbService.saveCoin(coin.getAsset(), coin.getFree()));
            balanceCoins = walletCoins.stream()
                    .map(b -> Balance.builder()
                            .coinName(b.getAsset())
                            .amount(b.getFree())
                            .build())
                    .toList();
        } else {
            log.info("Load coins from DynamoDB");
            balanceCoins = coinsFromDynamo.stream()
                    .map(w -> Balance.builder()
                            .coinName(w.getName())
                            .amount(w.getAmount())
                            .build())
                    .toList();
        }

        log.info("Balance coins: {}", balanceCoins);

        List<CoinPrice> walletPrices = client.getMinutesPrices(balanceCoins.stream()
                .map(Balance::getCoinName).toList(), "2m");
        if (walletPrices.size() == 1 && !USDT_COIN_NAME.equals(walletPrices.getFirst().getCoinName())) {
            Balance balance = balanceCoins.getFirst();
            CoinPrice price = walletPrices.getFirst();
            log.info("Split {} wallet coin, amount: {}", price.getCoinName(), balance.getAmount());

            BigDecimal halfAmount = balance.getAmount().divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_DOWN);
            dynamoDbService.saveCoin(balance.getCoinName(), halfAmount);
            log.info("Convert {} of {} to USDT", halfAmount, price.getCoinName());
            dynamoDbService.saveCoin(USDT_COIN_NAME, halfAmount.multiply(price.getLastPrice()));

            BigDecimal firstCoin = processCoin(price);
            BigDecimal secondCoin = processCoin(client.getUsdtCoinPrice());
            metricsService.submitWalletMetric(firstCoin.add(secondCoin));
        } else {
            BigDecimal total = walletPrices.stream()
                    .map(this::processCoin)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            metricsService.submitWalletMetric(total);
        }

        return null;
    }

    private BigDecimal processCoin(CoinPrice coinMinPrice) {
        log.info("Current coin price: {}", coinMinPrice);
        WalletCoin currentWalletCoin = dynamoDbService.getCoin(coinMinPrice.getCoinName());

        if (!USDT_COIN_NAME.equals(coinMinPrice.getCoinName())) {

            CoinOperationRecord prevRecord = dynamoDbService.getOperationById(currentWalletCoin.getLastOperation());

            if (prevRecord == null && coinMinPrice.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                log.info("No previous record found. Keep the coin {}", coinMinPrice.getCoinName());
                return currentWalletCoin.getAmount().multiply(coinMinPrice.getLastPrice());
            }

            if (prevRecord != null) {
                BigDecimal currentTotalPrice = coinMinPrice.getLastPrice().multiply(currentWalletCoin.getAmount());
                BigDecimal prevTotalPrice = BigDecimal.valueOf(Double.parseDouble(prevRecord.getBuyCoinPrice()))
                        .multiply(currentWalletCoin.getAmount());
                BigDecimal priceDiff = priceDiffService.priceDiff(prevTotalPrice, currentTotalPrice);

                log.info("Price diff check %: {}", priceDiff);
                if (priceDiff.doubleValue() < 15 && coinMinPrice.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Keep the coin: {}", coinMinPrice.getCoinName());
                    return currentWalletCoin.getAmount().multiply(coinMinPrice.getLastPrice());
                }
            }
        }

        List<String> coinsFromDynamo = new ArrayList<>(dynamoDbService.loadDynamoWallet().stream()
                .map(WalletCoin::getName)
                .toList());
        coinsFromDynamo.add(coinMinPrice.getCoinName());

        List<CoinPrice> growingCoins = client.getFullCoinPrices(coinsFromDynamo, "15m")
                .stream()
                .filter(this::filterCoinPrice)
                .sorted(Comparator.comparing(CoinPrice::getPriceChangePercent, Comparator.reverseOrder()))
                .toList();

        if (CollectionUtils.isNullOrEmpty(growingCoins)) {
            if (USDT_COIN_NAME.equals(coinMinPrice.getCoinName())) {
                log.info("Keep USDT coin");
                return currentWalletCoin.getAmount().multiply(coinMinPrice.getLastPrice());
            }
            log.info("No growing coins, converting to USDT");
            return  operationService.saveUsdtOperation(coinMinPrice, currentWalletCoin);
        }

        CoinPrice convertCoin = growingCoins.getFirst();
        log.info("Convert {} to {}", coinMinPrice.getCoinName(), convertCoin.getCoinName());
        return operationService.saveOperation(coinMinPrice, convertCoin, currentWalletCoin);
    }

    private boolean filterCoinPrice(CoinPrice coinPrice) {
        // check high and low barriers
        BigDecimal priceChange = coinPrice.getPriceChangePercent();
        double highPriceDiff = coinPrice.getPriceDiff();
        log.info("Coin: {}, high price diff: {}", coinPrice.getCoinName(), highPriceDiff);
        return priceChange.compareTo(BigDecimal.valueOf(0.2)) > 0 && highPriceDiff > -12;
    }
}
