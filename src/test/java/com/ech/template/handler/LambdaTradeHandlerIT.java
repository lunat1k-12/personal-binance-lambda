package com.ech.template.handler;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.impl.spot.Market;
import com.binance.connector.client.impl.spot.Wallet;
import com.ech.template.AbstractIT;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.IpCheckClient;
import com.ech.template.service.MetricsService;
import com.ech.template.service.OperationService;
import com.ech.template.service.PriceDiffService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

//@ExtendWith(MockitoExtension.class)
public class LambdaTradeHandlerIT extends AbstractIT {

    private LambdaTradeHandler handler;

    @Mock
    private SpotClient client;
    @Mock
    private Wallet wallet;
    @Mock
    private Market market;
    @Mock
    private CloudWatchClient cloudWatchClient;
    @Mock
    private IpCheckClient ipCheckClient;
    @Mock
    private MetricsService metricsService;

    private DynamoDbTable<WalletCoin> walletTable;
    private DynamoDbTable<CoinOperationRecord> coinTable;

//    @BeforeEach
    public void setup() {
        when(client.createWallet()).thenReturn(wallet);
        when(client.createMarket()).thenReturn(market);
        BinanceClient binanceClient = new BinanceClient(client, new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

        DynamoDbEnhancedClient dynamoDb = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        this.walletTable = dynamoDb.table("WalletCoin", TableSchema.fromBean(WalletCoin.class));
        this.coinTable = dynamoDb.table("CoinOperation", TableSchema.fromBean(CoinOperationRecord.class));
        DynamoDbService dynamoService = new DynamoDbService(
                walletTable,
                coinTable
        );
        this.handler = new LambdaTradeHandler(
                binanceClient,
                dynamoService,
                new OperationService(dynamoService, new PriceDiffService(cloudWatchClient, false), ipCheckClient),
                metricsService);
    }

//    @Test
    public void testCoinSplit() throws IOException {
        // given
        String balanceResponse = loadFile("balanceResponse.json");
        when(wallet.accountSnapshot(any())).thenReturn(balanceResponse);

        String coinPricesResponse = loadFile("xrpResponse.json");
        when(market.ticker(any())).thenReturn(coinPricesResponse);

        // do
        handler.handleRequest(new LambdaTradeHandler.LambdaInput(), null);

        // verify
        List<WalletCoin> coins = walletTable.scan().items().stream().toList();
        assertEquals(List.of("XRP", "USDT"), coins.stream()
                .map(WalletCoin::getName)
                .toList());
        assertEquals(BigDecimal.valueOf(38.09889357), coins.stream()
                .filter(c -> "XRP".equals(c.getName()))
                .map(WalletCoin::getAmount)
                .findFirst().orElse(null));
        assertEquals(BigDecimal.valueOf(21.228703497204), coins.stream()
                .filter(c -> "USDT".equals(c.getName()))
                .map(WalletCoin::getAmount)
                .findFirst().orElse(null));
    }

    private String loadFile(String fileName) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        Path jsonFilePath = Paths.get(classLoader.getResource(fileName).getPath());
        return Files.readString(jsonFilePath);
    }
}
