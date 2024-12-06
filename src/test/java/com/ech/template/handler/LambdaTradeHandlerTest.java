package com.ech.template.handler;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.IpCheckClient;
import com.ech.template.service.PriceDiffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LambdaTradeHandlerTest {

    private LambdaTradeHandler  lambdaTradeHandler;

    @Mock
    private BinanceClient client;
    @Mock
    private DynamoDbService dynamoDbService;
    @Mock
    private PriceDiffService priceDiffService;
    @Mock
    private IpCheckClient ipCheckClient;

    @BeforeEach
    public void setUp() {
        this.lambdaTradeHandler = new LambdaTradeHandler(client, dynamoDbService, priceDiffService, ipCheckClient);
    }

    @Test
    public void handleRequestKeepCoin() {
        // given
        List<WalletCoin> wallet = List.of(WalletCoin.builder()
                .name("COIN")
                .lastOperation(12L)
                .build());
        when(dynamoDbService.loadDynamoWallet()).thenReturn(wallet);
        List<String> walletCoins = List.of("COIN");
        CoinPrice coinPrice = new CoinPrice("COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(5), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        when(client.getMinutesPrices(eq(walletCoins))).thenReturn(List.of(coinPrice));

        // do
        lambdaTradeHandler.handleRequest(new LambdaTradeHandler.LambdaInput(), null);

        // verify
        verify(dynamoDbService, times(0)).getCoin(anyString());
        verify(dynamoDbService, times(0)).deleteCoinFromWallet(anyString());
        verify(dynamoDbService, times(0)).saveOperation(any());
        verify(dynamoDbService, times(0)).saveCoin(any(), any());
    }

    @Test
    public void handleRequestExchangeCoin() {
        // given
        List<WalletCoin> wallet = List.of(WalletCoin.builder()
                        .name("COIN")
                        .lastOperation(12L)
                .build());
        when(dynamoDbService.loadDynamoWallet()).thenReturn(wallet);
        List<String> walletCoins = List.of("COIN");
        CoinPrice coinPrice = new CoinPrice("COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(-2), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        when(client.getMinutesPrices(eq(walletCoins))).thenReturn(List.of(coinPrice));
        CoinPrice growPrice = new CoinPrice("NEW_COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(0.75), BigDecimal.valueOf(10), BigDecimal.ONE, BigDecimal.valueOf(9));
        when(client.getFullCoinPrices(eq("COIN"))).thenReturn(List.of(growPrice));
        when(dynamoDbService.getCoin(eq("COIN"))).thenReturn(wallet.getFirst());
        when(dynamoDbService.getOperationById(eq(12L))).thenReturn(CoinOperationRecord.builder()
                        .buyCoinPrice("1")
                .build());
        when(priceDiffService.getPriceDiff(any(), any())).thenReturn("1%");

        // do
        lambdaTradeHandler.handleRequest(new LambdaTradeHandler.LambdaInput(), null);

        // verify
        verify(dynamoDbService).loadDynamoWallet();
        verify(client).getMinutesPrices(eq(walletCoins));
        verify(client).getFullCoinPrices(eq("COIN"));
        verify(dynamoDbService).getCoin(eq("COIN"));
        verify(dynamoDbService).getOperationById(eq(12L));
        verify(priceDiffService).getPriceDiff(any(), any());

        verify(dynamoDbService).deleteCoinFromWallet(eq("COIN"));
        verify(dynamoDbService).saveCoin(eq("NEW_COIN"), anyLong());
        verify(dynamoDbService).saveOperation(any(CoinOperationRecord.class));
    }
}
