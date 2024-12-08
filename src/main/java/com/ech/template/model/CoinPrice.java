package com.ech.template.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@ToString
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

    public double getPriceDiff() {
        BigDecimal substractedHighPrice = highPrice.subtract(lowPrice);

        if (substractedHighPrice.equals(BigDecimal.ZERO)) {
            return 0d;
        }

        return lastPrice
                .subtract(lowPrice)
                .subtract(substractedHighPrice)
                .divide(substractedHighPrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
