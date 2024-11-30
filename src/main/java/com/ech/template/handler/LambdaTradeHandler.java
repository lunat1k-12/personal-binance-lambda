package com.ech.template.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.model.CoinPrice;
import com.ech.template.module.CommonModule;
import com.ech.template.service.DynamoDbService;
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

    public LambdaTradeHandler() {
        Injector injector = Guice.createInjector(new CommonModule());
        this.client = injector.getInstance(BinanceClient.class);
        this.dynamoDbService = injector.getInstance(DynamoDbService.class);
        this.priceDiffService = injector.getInstance(PriceDiffService.class);
    }

    public static void main(String[] args) {
        new LambdaTradeHandler().handleRequest(new LambdaInput(), null);
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

        client.getFiveMinutesPrices(balanceCoins)
                .forEach(this::processCoin);
        return null;
    }

    private void processCoin(CoinPrice coin5MinPrice) {
        // negative price change in 5 minutes
        if (!USDT_COIN_NAME.equals(coin5MinPrice.getCoinName()) &&
                coin5MinPrice.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0) {
            log.info("Keep the coin: {}", coin5MinPrice.getCoinName());
            return;
        }

        List<CoinPrice> growingCoins = client.getFullCoinPrices(coin5MinPrice.getCoinName())
                .stream()
                .filter(coin -> coin.getPriceChangePercent().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CoinPrice::getPriceChangePercent, Comparator.reverseOrder()))
                .toList();

        dynamoDbService.deleteCoinFromWallet(coin5MinPrice.getCoinName());
        if (CollectionUtils.isNullOrEmpty(growingCoins)) {
            if (USDT_COIN_NAME.equals(coin5MinPrice.getCoinName())) {
                log.info("Keep USDT coin");
                return;
            }
            log.info("No growing coins, converting to USDT");
            dynamoDbService.saveCoin(USDT_COIN_NAME);
            dynamoDbService.saveOperation(CoinOperationRecord.builder()
                            .id(Instant.now().toEpochMilli())
                            .sellCoinName(coin5MinPrice.getCoinName())
                            .sellCoinPrice(coin5MinPrice.getLastPrice().toPlainString())
                            .buyCoinName(USDT_COIN_NAME)
                            .buyCoinPrice("1")
                            .diffPercent(priceDiffService.getPriceDiff(coin5MinPrice,
                                    dynamoDbService.getPreviousOperation(coin5MinPrice.getCoinName())))
                    .build());
            return;
        }

        CoinPrice convertCoin = growingCoins.getFirst();
        log.info("Convert {} to {}", coin5MinPrice.getCoinName(), convertCoin.getCoinName());
        dynamoDbService.saveCoin(convertCoin.getCoinName());
        dynamoDbService.saveOperation(CoinOperationRecord.builder()
                .id(Instant.now().toEpochMilli())
                .sellCoinName(coin5MinPrice.getCoinName())
                .sellCoinPrice(coin5MinPrice.getLastPrice().toPlainString())
                .buyCoinName(convertCoin.getCoinName())
                .buyCoinPrice(convertCoin.getLastPrice().toPlainString())
                .diffPercent(priceDiffService.getPriceDiff(coin5MinPrice,
                        dynamoDbService.getPreviousOperation(coin5MinPrice.getCoinName())))
                .build());
    }
}
