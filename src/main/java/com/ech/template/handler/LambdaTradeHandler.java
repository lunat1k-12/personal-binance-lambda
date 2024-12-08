package com.ech.template.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.ech.template.model.BalanceResponse;
import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.module.CommonModule;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.OperationService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.utils.CollectionUtils;

import java.math.BigDecimal;
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

    public LambdaTradeHandler() {
        Injector injector = Guice.createInjector(new CommonModule());
        this.client = injector.getInstance(BinanceClient.class);
        this.dynamoDbService = injector.getInstance(DynamoDbService.class);
        this.operationService = injector.getInstance(OperationService.class);
    }

    @Override
    public Void handleRequest(LambdaInput lambdaInput, Context context) {

        List<WalletCoin> coinsFromDynamo = dynamoDbService.loadDynamoWallet();
        List<String> balanceCoins;
        if (CollectionUtils.isNullOrEmpty(coinsFromDynamo)) {
            log.info("Load coins from API");
            List<BalanceResponse.SnapshotVos.Data.Balance> walletCoins = client.getCurrentBalanceCoins();
            walletCoins.forEach(coin -> dynamoDbService.saveCoin(coin.getAsset(), coin.getFree()));
            balanceCoins = walletCoins.stream()
                    .map(BalanceResponse.SnapshotVos.Data.Balance::getAsset)
                    .toList();
        } else {
            log.info("Load coins from DynamoDB");
            balanceCoins = coinsFromDynamo.stream()
                    .map(WalletCoin::getName)
                    .toList();
        }

        log.info("Balance coins: {}", balanceCoins);

        client.getMinutesPrices(balanceCoins, "2m")
                .forEach(this::processCoin);
        return null;
    }

    private void processCoin(CoinPrice coinMinPrice) {
        log.info("Current coin price: {}", coinMinPrice);

        // negative price change in 2 minutes
        if (!USDT_COIN_NAME.equals(coinMinPrice.getCoinName()) &&
                coinMinPrice.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0) {
            log.info("Keep the coin: {}", coinMinPrice.getCoinName());
            return;
        }

        List<CoinPrice> growingCoins = client.getFullCoinPrices(coinMinPrice.getCoinName(), "15m")
                .stream()
                .filter(this::filterCoinPrice)
                .sorted(Comparator.comparing(CoinPrice::getPriceChangePercent, Comparator.reverseOrder()))
                .toList();

        WalletCoin currentWalletCoin = dynamoDbService.getCoin(coinMinPrice.getCoinName());

        if (CollectionUtils.isNullOrEmpty(growingCoins)) {
            if (USDT_COIN_NAME.equals(coinMinPrice.getCoinName())) {
                log.info("Keep USDT coin");
                return;
            }
            log.info("No growing coins, converting to USDT");
            operationService.saveUsdtOperation(coinMinPrice, currentWalletCoin);
            return;
        }

        CoinPrice convertCoin = growingCoins.getFirst();
        log.info("Convert {} to {}", coinMinPrice.getCoinName(), convertCoin.getCoinName());
        operationService.saveOperation(coinMinPrice, convertCoin, currentWalletCoin);
    }

    private boolean filterCoinPrice(CoinPrice coinPrice) {
        // check high and low barriers
        BigDecimal priceChange = coinPrice.getPriceChangePercent();
        double highPriceDiff = coinPrice.getPriceDiff();
        log.info("Coin: {}, high price diff: {}", coinPrice.getCoinName(), highPriceDiff);
        return priceChange.compareTo(BigDecimal.valueOf(0.2)) > 0 && highPriceDiff > -12;
    }
}
