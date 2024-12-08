package com.ech.template.service;

import com.binance.connector.client.SpotClient;
import com.ech.template.model.BalanceResponse;
import com.ech.template.model.CoinPrice;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.utils.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
@RequiredArgsConstructor
public class BinanceClient {

    private final SpotClient client;
    private final ObjectMapper objectMapper;

    public static final String USDT_COIN_NAME = "USDT";
    private static final Set<String> SKIP_COINS = Set.of("ETHW", "ETH");
    private static final Set<String> FULL_COINS_LIST = Set.of("SOL", "BNB", "AMP", "ADA", "DOGE",
            "NEAR", "NEIRO", "SHIB", "TAO", "WLD", "PNUT", "HBAR", "DOT", "LINK", "SAND", "VET",
            "CETUS", "COW", "1000SATS", "ACT", "FLOKI", "PEPE", "STRK", "THE", "WIN", "XLM", "SCR",
            "AUDIO", "FARM", "WING", "AR", "FLM", "DIA", "ARDR", "JUP", "BEL", "WOO", "UMA", "SCRT",
            "TUSD", "UNI", "WRX", "SKL", "FET", "LTC");

    public List<BalanceResponse.SnapshotVos.Data.Balance> getCurrentBalanceCoins() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "SPOT");

        String result = client.createWallet().accountSnapshot(parameters);
        log.info("balance response: {}", result);

        try {
            return getLastCoinNames(objectMapper.readValue(result, BalanceResponse.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<BalanceResponse.SnapshotVos.Data.Balance> getLastCoinNames(BalanceResponse balanceResponse) {
        List<BalanceResponse.SnapshotVos.Data.Balance> coinNames = new ArrayList<>();
        for (int i = balanceResponse.getSnapshotVos().size() - 1; i >= 0; i--) {
            BalanceResponse.SnapshotVos.Data data = balanceResponse.getSnapshotVos().get(i).getData();
            List<BalanceResponse.SnapshotVos.Data.Balance> dataCoinNames = data.getBalances().stream()
                    .filter(asset -> !SKIP_COINS.contains(asset.getAsset()))
                    .toList();

            if (!dataCoinNames.isEmpty()) {
                coinNames.addAll(dataCoinNames);
                break;
            }
        }

        return coinNames;
    }

    public List<CoinPrice> getMinutesPrices(List<String> coinNames, String windowSize) {
        List<String> list = new ArrayList<>();
        coinNames.stream()
                .filter(coin -> !USDT_COIN_NAME.equals(coin))
                .forEach(coinName -> list.add(coinName + "USDT"));

        List<CoinPrice> prices = new ArrayList<>();

        if (!CollectionUtils.isNullOrEmpty(list)) {
            prices.addAll(loadCoinPrices(list, windowSize));
        }

        if (coinNames.contains(USDT_COIN_NAME)) {
            prices.add(new CoinPrice(USDT_COIN_NAME,
                    BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.ONE));
        }
        return prices;
    }

    private List<CoinPrice> loadCoinPrices(List<String> symbols, String windowSize) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("symbols", symbols);
        parameters.put("windowSize", windowSize);
        String result = client.createMarket().ticker(parameters);
        log.info("Coin prices: {}", result);

        try {
            return objectMapper.readValue(result, new TypeReference<List<CoinPrice>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public List<CoinPrice> getFullCoinPrices(String coinNameToSkip, String windowSize) {
        return getMinutesPrices(FULL_COINS_LIST.stream()
                // Substring USDT at the end
                .filter(coinName -> !coinName.equals(coinNameToSkip))
                .toList(), windowSize);
    }

}
