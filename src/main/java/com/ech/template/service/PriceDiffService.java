package com.ech.template.service;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Log4j2
@RequiredArgsConstructor
public class PriceDiffService {

    private static final String NA = "N/A";
    private static final String POSITIVE_METRIC_NAME = "PositiveSellCount";
    private static final String POSITIVE_METRIC_TOTAL_NAME = "PositiveTotalSellCount";
    private static final String NEGATIVE_METRIC_NAME = "NegativeSellCount";
    private static final String NEGATIVE_METRIC_TOTAL_NAME = "NegativeTotalSellCount";
    private static final String METRIC_NAMESPACE = "Crypto";
    private static final String LOCAL_METRIC_NAMESPACE = "CryptoTest";
    private static final String WALLET_TOTAL_METRIC_NAME = "WalletUSDT";

    private final CloudWatchClient cloudWatchClient;
    private final Boolean isLocal;

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
            this.submitMetric(currentPrice.getCoinName(), result.doubleValue(), NEGATIVE_METRIC_NAME);
            this.submitMetric(result.abs().doubleValue(), NEGATIVE_METRIC_TOTAL_NAME);
        } else {
            log.info("Coin price increased by {} %", result.toPlainString());
            this.submitMetric(currentPrice.getCoinName(), result.doubleValue(), POSITIVE_METRIC_NAME);
            this.submitMetric(result.doubleValue(), POSITIVE_METRIC_TOTAL_NAME);
        }
        return String.format(Locale.CANADA, "%.2f%%", result.doubleValue());
    }

    private void submitMetric(String coinName, Double value, String metricName) {
        MetricDatum metric = MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .dimensions(Dimension.builder()
                        .name("CoinName")
                        .value(coinName)
                        .build())
                .unit(StandardUnit.COUNT)
                .build();

        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(isLocal ? LOCAL_METRIC_NAMESPACE : METRIC_NAMESPACE)
                .metricData(metric)
                .build();

        cloudWatchClient.putMetricData(request);
        log.info("Submit {} metric for {}", metric, coinName);
    }

    private void submitMetric(Double value, String metricName) {
        MetricDatum metric = MetricDatum.builder()
                .metricName(metricName)
                .value(value)
                .unit(StandardUnit.COUNT)
                .build();

        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(isLocal ? LOCAL_METRIC_NAMESPACE : METRIC_NAMESPACE)
                .metricData(metric)
                .build();

        cloudWatchClient.putMetricData(request);
        log.info("Submit {} metric", metric);
    }

    public BigDecimal getConvertedAmount(BigDecimal amount, CoinPrice sourceCoin, CoinPrice targetCoin) {
        BigDecimal usdtSourceCost = amount.multiply(sourceCoin.getLastPrice());

        this.submitMetric(usdtSourceCost.doubleValue(), WALLET_TOTAL_METRIC_NAME);
        return usdtSourceCost.divide(targetCoin.getLastPrice(), 8, RoundingMode.HALF_DOWN);
    }

    public BigDecimal getConvertedToUsdtAmount(BigDecimal amount, CoinPrice sourceCoin) {
        BigDecimal usdtSourceCost = amount.multiply(sourceCoin.getLastPrice());

        this.submitMetric(usdtSourceCost.doubleValue(), WALLET_TOTAL_METRIC_NAME);
        return usdtSourceCost.divide(BigDecimal.ONE, 8, RoundingMode.HALF_DOWN);
    }
}
