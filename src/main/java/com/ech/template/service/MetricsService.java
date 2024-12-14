package com.ech.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.math.BigDecimal;

@Log4j2
@RequiredArgsConstructor
public class MetricsService {

    private static final String METRIC_NAMESPACE = "Crypto";
    private static final String LOCAL_METRIC_NAMESPACE = "CryptoTest";
    private static final String WALLET_TOTAL_METRIC_NAME = "WalletUSDT";

    private final CloudWatchClient cloudWatchClient;
    private final Boolean isLocal;

    public void submitWalletMetric(BigDecimal totalAmount) {
        this.submitMetric(totalAmount.doubleValue(), WALLET_TOTAL_METRIC_NAME);
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
}
