package com.ech.template.service;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.spot.Market;
import com.binance.connector.client.impl.spot.Wallet;
import com.ech.template.model.BalanceResponse;
import com.ech.template.model.CoinPrice;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BinanceClientTest {

    private BinanceClient binanceClient;

    @Mock
    private SpotClient client;

    @Mock
    private Wallet wallet;

    @Mock
    private Market market;

    @BeforeEach
    public void setUp() {
        this.binanceClient = new BinanceClient(client, new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    @Test
    public void getCurrentBalanceCoins() throws IOException {
        // given
        when(client.createWallet()).thenReturn(wallet);
        String balanceResponse = loadFile("balanceResponse.json");

        when(wallet.accountSnapshot(any())).thenReturn(balanceResponse);

        // do
        List<String> result = binanceClient.getCurrentBalanceCoins()
                .stream()
                .map(BalanceResponse.SnapshotVos.Data.Balance::getAsset)
                .toList();

        // verify
        assertEquals(List.of("XRP"), result);
        verify(wallet).accountSnapshot(any());
    }

    @Test
    public void getMinutesPrices() throws IOException {
        // given
        when(client.createMarket()).thenReturn(market);
        String coinPricesResponse = loadFile("pricesResponse.json");
        when(market.ticker(any())).thenReturn(coinPricesResponse);

        // do
        List<CoinPrice> prices = binanceClient.getMinutesPrices(List.of("COIN"), "2m");

        // verify
        assertEquals(45, prices.size());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(market).ticker(captor.capture());
        Map<String, Object> map = captor.getValue();
        assertEquals("2m", map.get("windowSize"));
        assertEquals(List.of("COINUSDT"), map.get("symbols"));
    }

    @Test
    public void getFullCoinPrices() throws IOException {
        // given
        when(client.createMarket()).thenReturn(market);
        String coinPricesResponse = loadFile("pricesResponse.json");
        when(market.ticker(any())).thenReturn(coinPricesResponse);

        // do
        List<CoinPrice> prices = binanceClient.getFullCoinPrices(List.of("WLD"), "15m");

        // verify
        assertEquals(45, prices.size());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(market).ticker(captor.capture());
        Map<String, Object> map = captor.getValue();
        assertEquals("15m", map.get("windowSize"));

        List<String> coins = (List<String>) map.get("symbols");
        assertFalse(coins.contains("WLD"));
    }

    private String loadFile(String fileName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        Path jsonFilePath = Paths.get(classLoader.getResource(fileName).getPath());
        return Files.readString(jsonFilePath);
    }
}
