package com.ech.template.service;

import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.math.BigDecimal;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public class DynamoDbService {

    private final DynamoDbTable<WalletCoin> walletCoinTable;
    private final DynamoDbTable<CoinOperationRecord> operationTable;

    public void saveCoin(String coin, BigDecimal amount) {
        this.saveCoin(coin, amount, null);
    }

    public void saveCoin(String coin, BigDecimal amount, Long operationId) {
        walletCoinTable.putItem(WalletCoin.builder()
                .name(coin)
                .lastOperation(operationId)
                .amount(amount)
                .build());
        log.info("Coin saved to wallet: {}", coin);
    }

    public void deleteCoinFromWallet(String coin) {
        walletCoinTable.deleteItem(WalletCoin.builder()
                        .name(coin)
                .build());
        log.info("Coin deleted from wallet: {}", coin);
    }

    public WalletCoin getCoin(String coin) {
        return walletCoinTable.getItem(Key.builder()
                        .partitionValue(coin)
                .build());
    }

    public List<WalletCoin> loadDynamoWallet() {
        return walletCoinTable.scan().items().stream()
                .toList();
    }

    public void saveOperation(CoinOperationRecord operation) {
        operationTable.putItem(operation);
        log.info("Store operation: {}", operation);
    }

    public CoinOperationRecord getOperationById(Long id) {
        if (id == null) {
            return null;
        }

        return operationTable.getItem(Key.builder()
                        .partitionValue(id)
                .build());
    }
}
