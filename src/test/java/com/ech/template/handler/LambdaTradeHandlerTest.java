package com.ech.template.handler;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.IpCheckClient;
import com.ech.template.service.MetricsService;
import com.ech.template.service.OperationService;
import com.ech.template.service.PriceDiffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.ech.template.service.BinanceClient.USDT_COIN_NAME;
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
    @Mock
    private MetricsService metricsService;

    @BeforeEach
    public void setUp() {
        this.lambdaTradeHandler = new LambdaTradeHandler(client,
                dynamoDbService, new OperationService(dynamoDbService, priceDiffService, ipCheckClient),
                metricsService);
    }

    @Test
    public void handleRequestKeepCoin() {
        // given
        List<WalletCoin> wallet = List.of(WalletCoin.builder()
                .name("COIN")
                .lastOperation(12L)
                .amount(BigDecimal.valueOf(200))
                .build());
        when(dynamoDbService.loadDynamoWallet()).thenReturn(wallet);
        List<String> walletCoins = List.of("COIN");
        CoinPrice coinPrice = new CoinPrice("COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(5), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        when(client.getMinutesPrices(eq(walletCoins), eq("2m"))).thenReturn(List.of(coinPrice));

        CoinPrice usdtCoin = new CoinPrice(USDT_COIN_NAME,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE);
        when(client.getUsdtCoinPrice()).thenReturn(usdtCoin);

        when(dynamoDbService.getCoin("COIN")).thenReturn(wallet.getFirst());
        when(dynamoDbService.getCoin(eq("USDT"))).thenReturn(WalletCoin.builder()
                .name("USDT")
                .amount(BigDecimal.valueOf(1))
                .lastOperation(1L).build());

        // do
        lambdaTradeHandler.handleRequest(new LambdaTradeHandler.LambdaInput(), null);

        // verify
        verify(dynamoDbService, times(2)).getCoin(anyString());
        verify(dynamoDbService, times(0)).deleteCoinFromWallet(anyString());
        verify(dynamoDbService, times(0)).saveOperation(any());
        verify(dynamoDbService, times(2)).saveCoin(any(), any());
    }

    @Test
    public void handleRequestExchangeCoin() {
        // given
        List<WalletCoin> wallet = List.of(WalletCoin.builder()
                        .name("COIN")
                        .amount(BigDecimal.valueOf(100))
                        .lastOperation(12L)
                .build());
        when(dynamoDbService.loadDynamoWallet()).thenReturn(wallet);
        List<String> walletCoins = List.of("COIN");
        CoinPrice coinPrice = new CoinPrice("COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(-2), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        when(client.getMinutesPrices(eq(walletCoins), eq("2m"))).thenReturn(List.of(coinPrice));
        CoinPrice growPrice = new CoinPrice("NEW_COINUSDT",
                BigDecimal.ONE, BigDecimal.valueOf(0.75), BigDecimal.valueOf(10), BigDecimal.ONE, BigDecimal.valueOf(9));
        when(client.getFullCoinPrices(eq(List.of("COIN", "COIN")), eq("15m"))).thenReturn(List.of(growPrice));
        when(dynamoDbService.getCoin(eq("COIN"))).thenReturn(wallet.getFirst());
        when(dynamoDbService.getOperationById(eq(12L))).thenReturn(CoinOperationRecord.builder()
                        .buyCoinPrice("1")
                .build());
        when(priceDiffService.getPriceDiff(any(), any())).thenReturn("1%");
        when(priceDiffService.getConvertedAmount(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));

        CoinPrice usdtCoin = new CoinPrice(USDT_COIN_NAME,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE);
        when(client.getUsdtCoinPrice()).thenReturn(usdtCoin);
        when(dynamoDbService.getCoin(eq("USDT"))).thenReturn(WalletCoin.builder()
                .name("USDT")
                .amount(BigDecimal.valueOf(1))
                .lastOperation(1L).build());

        // do
        lambdaTradeHandler.handleRequest(new LambdaTradeHandler.LambdaInput(), null);

        // verify
        verify(dynamoDbService, times(3)).loadDynamoWallet();
        verify(client).getMinutesPrices(eq(walletCoins), eq("2m"));
        verify(client, times(2)).getFullCoinPrices(any(), anyString());
        verify(dynamoDbService).getCoin(eq("COIN"));
        verify(dynamoDbService).getOperationById(eq(12L));
        verify(priceDiffService).getPriceDiff(any(), any());

        verify(dynamoDbService).deleteCoinFromWallet(eq("COIN"));
        verify(dynamoDbService).saveCoin(eq("NEW_COIN"), any(), anyLong());
        verify(dynamoDbService).saveOperation(any(CoinOperationRecord.class));
    }
}
