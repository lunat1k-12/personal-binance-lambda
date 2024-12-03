package com.ech.template.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.model.CoinPrice;
import com.ech.template.module.CommonModule;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.IpCheckClient;
import com.ech.template.service.PriceDiffService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.utils.CollectionUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static com.ech.template.service.BinanceClient.USDT_COIN_NAME;

@Log4j2
public class LambdaTradeHandler implements RequestHandler<LambdaTradeHandler.LambdaInput, Void> {

    public static class LambdaInput {}

    private final BinanceClient client;
    private final DynamoDbService dynamoDbService;
    private final PriceDiffService priceDiffService;
    private final IpCheckClient ipCheckClient;

    public LambdaTradeHandler() {
        Injector injector = Guice.createInjector(new CommonModule());
        this.client = injector.getInstance(BinanceClient.class);
        this.dynamoDbService = injector.getInstance(DynamoDbService.class);
        this.priceDiffService = injector.getInstance(PriceDiffService.class);
        this.ipCheckClient = injector.getInstance(IpCheckClient.class);
    }

    @Override
    public Void handleRequest(LambdaInput lambdaInput, Context context) {

        List<WalletCoin> coinsFromDynamo = dynamoDbService.loadDynamoWallet();
        List<String> balanceCoins;
        if (CollectionUtils.isNullOrEmpty(coinsFromDynamo)) {
            log.info("Load coins from API");
            balanceCoins = client.getCurrentBalanceCoins();
            balanceCoins.forEach(dynamoDbService::saveCoin);
        } else {
            log.info("Load coins from DynamoDB");
            balanceCoins = coinsFromDynamo.stream()
                    .map(WalletCoin::getName)
                    .toList();
        }

        log.info("Balance coins: {}", balanceCoins);

        client.getMinutesPrices(balanceCoins)
                .forEach(this::processCoin);
        return null;
    }

    private void processCoin(CoinPrice coinMinPrice) {
        // negative price change in 2 minutes
        if (!USDT_COIN_NAME.equals(coinMinPrice.getCoinName()) &&
                coinMinPrice.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0) {
            log.info("Keep the coin: {}", coinMinPrice.getCoinName());
            return;
        }

        List<CoinPrice> growingCoins = client.getFullCoinPrices(coinMinPrice.getCoinName())
                .stream()
                .filter(this::filterCoinPrice)
                .sorted(Comparator.comparing(CoinPrice::getPriceChangePercent, Comparator.reverseOrder()))
                .toList();

        Long lastOperationId = dynamoDbService.getCoin(coinMinPrice.getCoinName()).getLastOperation();
        dynamoDbService.deleteCoinFromWallet(coinMinPrice.getCoinName());
        Long operationId = Instant.now().toEpochMilli();
        if (CollectionUtils.isNullOrEmpty(growingCoins)) {
            if (USDT_COIN_NAME.equals(coinMinPrice.getCoinName())) {
                log.info("Keep USDT coin");
                return;
            }
            log.info("No growing coins, converting to USDT");
            dynamoDbService.saveCoin(USDT_COIN_NAME, operationId);
            dynamoDbService.saveOperation(CoinOperationRecord.builder()
                            .id(operationId)
                            .sellCoinName(coinMinPrice.getCoinName())
                            .sellCoinPrice(coinMinPrice.getLastPrice().toPlainString())
                            .buyCoinName(USDT_COIN_NAME)
                            .buyCoinPrice("1")
                            .diffPercent(priceDiffService.getPriceDiff(coinMinPrice,
                                    dynamoDbService.getOperationById(lastOperationId)))
                            .ipAddress(ipCheckClient.getMyIp())
                            .priceChangePercent(BigDecimal.ZERO.toPlainString())
                    .build());
            return;
        }

        CoinPrice convertCoin = growingCoins.getFirst();
        log.info("Convert {} to {}", coinMinPrice.getCoinName(), convertCoin.getCoinName());
        dynamoDbService.saveCoin(convertCoin.getCoinName(), operationId);
        dynamoDbService.saveOperation(CoinOperationRecord.builder()
                .id(operationId)
                .sellCoinName(coinMinPrice.getCoinName())
                .sellCoinPrice(coinMinPrice.getLastPrice().toPlainString())
                .buyCoinName(convertCoin.getCoinName())
                .buyCoinPrice(convertCoin.getLastPrice().toPlainString())
                .diffPercent(priceDiffService.getPriceDiff(coinMinPrice,
                        dynamoDbService.getOperationById(lastOperationId)))
                .ipAddress(ipCheckClient.getMyIp())
                .priceChangePercent(convertCoin.getPriceChangePercent().toPlainString())
                .build());
    }

    private boolean filterCoinPrice(CoinPrice coinPrice) {
        // check high and low barriers
        BigDecimal priceChange = coinPrice.getPriceChangePercent();
        return priceChange.compareTo(BigDecimal.valueOf(0.6)) > 0 &&
                priceChange.compareTo(BigDecimal.valueOf(1.1)) < 0;
    }
}
