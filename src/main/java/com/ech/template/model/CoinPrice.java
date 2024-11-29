package com.ech.template.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class CoinPrice {

    @JsonCreator
    public CoinPrice(@JsonProperty("symbol") String symbol,
                     @JsonProperty("priceChange") BigDecimal priceChange,
                     @JsonProperty("priceChangePercent") BigDecimal priceChangePercent,
                     @JsonProperty("highPrice") BigDecimal highPrice,
                     @JsonProperty("lowPrice") BigDecimal lowPrice,
                     @JsonProperty("lastPrice") BigDecimal lastPrice) {
        this.symbol = symbol;
        this.priceChange = priceChange;
        this.priceChangePercent = priceChangePercent;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.lastPrice = lastPrice;
    }

    private final String symbol;
    private final BigDecimal priceChange;
    private final BigDecimal priceChangePercent;

    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal lastPrice;

    public String getCoinName() {
        if ("USDT".equals(symbol)) {
            return symbol;
        }
        return symbol.substring(0, symbol.length() - 4);
    }
}
