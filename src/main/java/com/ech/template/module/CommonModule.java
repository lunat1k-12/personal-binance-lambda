package com.ech.template.module;

import com.binance.connector.client.impl.SpotClientImpl;
import com.ech.template.model.dynamodb.CoinOperationRecord;
import com.ech.template.model.dynamodb.WalletCoin;
import com.ech.template.service.BinanceClient;
import com.ech.template.service.DynamoDbService;
import com.ech.template.service.PriceDiffService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.utils.CollectionUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Log4j2
public class CommonModule extends AbstractModule {

    private final boolean isLocalLambda = System.getenv("BINANCE_LOCAL_DYNAMO").equals(Boolean.TRUE.toString());

    @Provides
    @Singleton
    public BinanceClient buildBinanceClient(ObjectMapper objectMapper) throws JsonProcessingException {
        if (isLocalLambda) {
            log.info("Build binance client with local variables");
            return new BinanceClient(new SpotClientImpl(
                    System.getenv("SPOT_API_KEY"),
                    System.getenv("SPOT_SECRET")),
                    objectMapper
            );
        }

        log.info("Build binance client with Secret variables");
        // retrieve keys from Secret Manager
        try (SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(Region.US_EAST_1)
                .build()) {
            // Define the secret name
            String secretName = "CryptoLambda";

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretString = response.secretString();

            Map<String, String> secretMap =
                    objectMapper.readValue(secretString, new TypeReference<Map<String, String>>() {});

            return new BinanceClient(new SpotClientImpl(
                    secretMap.get("SPOT_API_KEY"),
                    secretMap.get("SPOT_SECRET")),
                    objectMapper
            );
        } catch (Exception e) {
            log.error("Failed to retrieve secret", e);
            throw e;
        }
    }

    @Provides
    @Singleton
    public ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Provides
    @Singleton
    public DynamoDbClient dynamoDbClient() {

        if (isLocalLambda) {
            DynamoDbClient client = DynamoDbClient.builder()
                    .endpointOverride(URI.create("http://localhost:8000"))  // Local endpoint
                    .region(Region.US_EAST_1)                               // Required for AWS SDK
                    .build();
            initTables(client);
            return client;
        }

        return DynamoDbClient.builder()
                .region(Region.EU_WEST_2)
                .build();
    }

    @Provides
    @Singleton
    public DynamoDbEnhancedClient buildDynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Provides
    @Singleton
    public PriceDiffService buildPriceDiffService() {
        return new PriceDiffService();
    }

    @Provides
    @Singleton
    public DynamoDbService buildDynamoDbService(DynamoDbEnhancedClient client) {
        return new DynamoDbService(
                client.table("WalletCoin", TableSchema.fromBean(WalletCoin.class)),
                client.table("CoinOperation", TableSchema.fromBean(CoinOperationRecord.class)));
    }

    private void initTables(DynamoDbClient dynamoDbClient) {
        ListTablesRequest request = ListTablesRequest.builder().build();
        ListTablesResponse response = dynamoDbClient.listTables(request);

        if (response.getValueForField("TableNames", List.class)
                .filter(l -> !CollectionUtils.isNullOrEmpty(l))
                .isEmpty()) {

            log.info("Create local dynamo tables");
            CreateTableRequest walletCoinTableCreate = CreateTableRequest.builder()
                    .tableName("WalletCoin")
                    .keySchema(KeySchemaElement.builder()
                                    .attributeName("CoinName")
                                    .keyType(KeyType.HASH)
                                    .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                                    .attributeName("CoinName")
                                    .attributeType(ScalarAttributeType.S) // N for Number, S for String
                                    .build())
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L) // Set read capacity
                            .writeCapacityUnits(5L) // Set write capacity
                            .build())
                    .build();

            CreateTableRequest coinOperationTableCreate = CreateTableRequest.builder()
                    .tableName("CoinOperation")
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("id")
                            .keyType(KeyType.HASH)
                            .build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("id")
                            .attributeType(ScalarAttributeType.N) // N for Number, S for String
                            .build())
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L) // Set read capacity
                            .writeCapacityUnits(5L) // Set write capacity
                            .build())
                    .build();

            dynamoDbClient.createTable(walletCoinTableCreate);
            dynamoDbClient.createTable(coinOperationTableCreate);
        }
    }
}
