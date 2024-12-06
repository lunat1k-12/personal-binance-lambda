package com.ech.template.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Setter
@DynamoDbBean
public class CoinOperationRecord {

    private Long id;

    private String sellCoinName;

    private String sellCoinPrice;

    private String buyCoinName;

    private String buyCoinPrice;

    private String diffPercent;

    private String ipAddress;

    private String priceChangePercent;

    private Double highPriceDiff;

    @DynamoDbPartitionKey
    public Long getId() {
        return id;
    }

    @DynamoDbAttribute("SellCoinName")
    public String getSellCoinName() {
        return sellCoinName;
    }

    @DynamoDbAttribute("SellCoinPrice")
    public String getSellCoinPrice() {
        return sellCoinPrice;
    }

    @DynamoDbAttribute("BuyCoinName")
    public String getBuyCoinName() {
        return buyCoinName;
    }

    @DynamoDbAttribute("BuyCoinPrice")
    public String getBuyCoinPrice() {
        return buyCoinPrice;
    }

    @DynamoDbAttribute("DiffPercent")
    public String getDiffPercent() {
        return diffPercent;
    }

    @DynamoDbAttribute("IPAddress")
    public String getIpAddress() {
        return ipAddress;
    }

    @DynamoDbAttribute("PriceChangePercent")
    public String getPriceChangePercent() {
        return priceChangePercent;
    }

    @DynamoDbAttribute("HighPriceDiff")
    public Double getHighPriceDiff() {
        return highPriceDiff;
    }
}
