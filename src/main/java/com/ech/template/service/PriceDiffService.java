package com.ech.template.service;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Log4j2
public class PriceDiffService {

    private static final String NA = "N/A";

    public String getPriceDiff(CoinPrice currentPrice, CoinOperationRecord oldPriceOperation) {

        if (oldPriceOperation == null) {
            log.info("Can't calculate diff for {}", currentPrice);
            return NA;
        }

        BigDecimal oldPrice = new BigDecimal(oldPriceOperation.getBuyCoinPrice());
        BigDecimal newPrice = currentPrice.getLastPrice();

        BigDecimal result = newPrice.subtract(oldPrice)
                .divide(oldPrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            log.info("Coin price dropped by {} %", result.toPlainString());
        } else {
            log.info("Coin price increased by {} %", result.toPlainString());
        }
        return String.format(Locale.CANADA, "%.2f%%", result.doubleValue());
    }
}
