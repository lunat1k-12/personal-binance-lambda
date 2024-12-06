package com.ech.template.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoinPriceTest {

    @Test
    public void priceDiffBiggerHighPrice() {
        double diff = new CoinPrice("Coin", BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(100),
                BigDecimal.ZERO,
                BigDecimal.valueOf(85))
                .getPriceDiff();
        assertEquals(-15, diff);
    }

    @Test
    public void priceDiffSmallerHighPrice() {
        double diff = new CoinPrice("Coin", BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(80),
                BigDecimal.ZERO,
                BigDecimal.valueOf(100))
                .getPriceDiff();
        assertEquals(25, diff);
    }

    @Test
    public void priceDiffRealDataPrice() {
        double diff = new CoinPrice("Coin", BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(0.01160),
                BigDecimal.ZERO,
                BigDecimal.valueOf(0.008573))
                .getPriceDiff();
        assertEquals(-26.094828, diff);
    }

    @Test
    public void priceDiffWithLowPrice() {
        double diff = new CoinPrice("Coin", BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.valueOf(120),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(100))
                .getPriceDiff();
        assertEquals(-20, diff);
    }

    @Test
    public void usdtPriceDiff() {
        double diff = new CoinPrice("USDT",
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE)
                .getPriceDiff();
        assertEquals(0, diff);
    }
}
