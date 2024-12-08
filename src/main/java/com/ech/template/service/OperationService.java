package com.ech.template.service;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

import static com.ech.template.service.BinanceClient.USDT_COIN_NAME;

@RequiredArgsConstructor
public class OperationService {
    private final DynamoDbService dynamoDbService;
    private final PriceDiffService priceDiffService;
    private final IpCheckClient ipCheckClient;

    public void saveUsdtOperation(CoinPrice sourcePrice, WalletCoin currentWalletCoin) {
        Long operationId = Instant.now().toEpochMilli();

        dynamoDbService.deleteCoinFromWallet(sourcePrice.getCoinName());
        dynamoDbService.saveCoin(USDT_COIN_NAME,
                priceDiffService.getConvertedToUsdtAmount(currentWalletCoin.getAmount(), sourcePrice),
                operationId);
        dynamoDbService.saveOperation(CoinOperationRecord.builder()
                .id(operationId)
                .sellCoinName(sourcePrice.getCoinName())
                .sellCoinPrice(sourcePrice.getLastPrice().toPlainString())
                .buyCoinName(USDT_COIN_NAME)
                .buyCoinPrice("1")
                .diffPercent(priceDiffService.getPriceDiff(sourcePrice,
                        dynamoDbService.getOperationById(currentWalletCoin.getLastOperation())))
                .ipAddress(ipCheckClient.getMyIp())
                .priceChangePercent(BigDecimal.ZERO.toPlainString())
                .build());
    }

    public void saveOperation(CoinPrice sourcePrice, CoinPrice convertCoin, WalletCoin currentWalletCoin) {
        Long operationId = Instant.now().toEpochMilli();

        dynamoDbService.deleteCoinFromWallet(sourcePrice.getCoinName());
        dynamoDbService.saveCoin(convertCoin.getCoinName(),
                priceDiffService.getConvertedAmount(currentWalletCoin.getAmount(), sourcePrice, convertCoin),
                operationId);
        dynamoDbService.saveOperation(CoinOperationRecord.builder()
                .id(operationId)
                .sellCoinName(sourcePrice.getCoinName())
                .sellCoinPrice(sourcePrice.getLastPrice().toPlainString())
                .buyCoinName(convertCoin.getCoinName())
                .buyCoinPrice(convertCoin.getLastPrice().toPlainString())
                .diffPercent(priceDiffService.getPriceDiff(sourcePrice,
                        dynamoDbService.getOperationById(currentWalletCoin.getLastOperation())))
                .ipAddress(ipCheckClient.getMyIp())
                .priceChangePercent(convertCoin.getPriceChangePercent().toPlainString())
                .highPriceDiff(convertCoin.getPriceDiff())
                .build());
    }
}
