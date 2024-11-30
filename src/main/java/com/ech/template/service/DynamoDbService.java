package com.ech.template.service;

import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class DynamoDbService {

    private final DynamoDbTable<WalletCoin> walletCoinTable;
    private final DynamoDbTable<CoinOperationRecord> operationTable;

    public void saveCoin(String coin) {
        walletCoinTable.putItem(WalletCoin.builder()
                .name(coin)
                .build());
        log.info("Coin saved to wallet: " + coin);
    }

    public void deleteCoinFromWallet(String coin) {
        walletCoinTable.deleteItem(WalletCoin.builder()
                        .name(coin)
                .build());
        log.info("Coin deleted from wallet: {}", coin);
    }

    public List<WalletCoin> loadDynamoWallet() {
        return walletCoinTable.scan().items().stream()
                .toList();
    }

    public void saveOperation(CoinOperationRecord operation) {
        operationTable.putItem(operation);
    }

    public CoinOperationRecord getPreviousOperation(String coin) {
        return operationTable.scan(ScanEnhancedRequest.builder()
                        .filterExpression(Expression.builder()
                                .expression("BuyCoinName = :coinName")
                                .putExpressionValue(":coinName", AttributeValue.builder()
                                        .s(coin)
                                        .build())
                                .build())
                .build())
                .items().stream()
                .max(Comparator.comparing(CoinOperationRecord::getId))
                .orElse(null);
    }
}
