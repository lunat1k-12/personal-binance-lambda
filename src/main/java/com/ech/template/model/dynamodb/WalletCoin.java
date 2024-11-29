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
public class WalletCoin {

    private String name;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("CoinName")
    public String getName() {
        return name;
    }
}
