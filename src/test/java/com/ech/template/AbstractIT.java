package com.ech.template;

import org.junit.After;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.LineIterator;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
import software.amazon.awssdk.utils.CollectionUtils;

import java.net.URI;
import java.util.List;

public abstract class AbstractIT {
    private static GenericContainer<?> dynamoDbContainer;
    protected static DynamoDbClient dynamoDbClient;

    @BeforeAll
    public static void setupContainer() {
        // Start DynamoDB Local container
        dynamoDbContainer = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
                .withExposedPorts(8000); // Default port for DynamoDB Local
        dynamoDbContainer.start();

        // Configure DynamoDB client to connect to the container
        String endpoint = String.format("http://%s:%d",
                dynamoDbContainer.getHost(),
                dynamoDbContainer.getFirstMappedPort());
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
//                .credentialsProvider(StaticCredentialsProvider.create(
//                        AwsBasicCredentials.create("fake-access-key", "fake-secret-key")))
                .build();

        initTables();
    }

    private static void initTables() {
        ListTablesRequest request = ListTablesRequest.builder().build();
        ListTablesResponse response = dynamoDbClient.listTables(request);

        if (response.getValueForField("TableNames", List.class)
                .filter(l -> !CollectionUtils.isNullOrEmpty(l))
                .isEmpty()) {

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

    @AfterAll
    public static void tearDown() {
        if (dynamoDbClient != null) {
            dynamoDbClient.close();
        }
        if (dynamoDbContainer != null) {
            dynamoDbContainer.stop();
        }
    }
}
