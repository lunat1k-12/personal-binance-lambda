package com.ech.template.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class Balance {
    private String coinName;
    private BigDecimal amount;
}
