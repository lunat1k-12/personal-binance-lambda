package com.ech.template.service;

import com.ech.template.model.CoinPrice;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PriceDiffServiceTest {

    private PriceDiffService priceDiffService;

    @Mock
    private CloudWatchClient cloudWatchClient;

    @BeforeEach
    public void setUp() {
        this.priceDiffService = new PriceDiffService(cloudWatchClient, true);
    }

    @Test
    public void testPriceDrop() {
        // given
        CoinPrice price = new CoinPrice("ADAUSDT",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(0.09));

        CoinOperationRecord record = CoinOperationRecord.builder()
                .buyCoinPrice("0.1")
                .build();

        // do
        String res = priceDiffService.getPriceDiff(price, record);

        // verify
        assertEquals("-10.00%", res);
        verify(cloudWatchClient, times(2)).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    public void testPriceUp() {
        // given
        CoinPrice price = new CoinPrice("ADAUSDT",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(0.11));

        CoinOperationRecord record = CoinOperationRecord.builder()
                .buyCoinPrice("0.1")
                .build();

        // do
        String res = priceDiffService.getPriceDiff(price, record);

        // verify
        assertEquals("10.00%", res);
        verify(cloudWatchClient, times(2)).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    public void testPriceDown() {
        // given
        CoinPrice price = new CoinPrice("ADAUSDT",
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(2.88000000));

        CoinOperationRecord record = CoinOperationRecord.builder()
                .buyCoinPrice("2.88300000")
                .build();

        // do
        String res = priceDiffService.getPriceDiff(price, record);

        // verify
        assertEquals("-0.10%", res);
        verify(cloudWatchClient, times(2)).putMetricData(any(PutMetricDataRequest.class));
    }
}
