package com.ech.template.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class BalanceResponse {

    @JsonCreator
    public BalanceResponse(@JsonProperty("code") Integer code,
                           @JsonProperty("snapshotVos") List<SnapshotVos> snapshotVos) {
        this.code = code;
        this.snapshotVos = snapshotVos;
    }

    private final Integer code;
    private final List<SnapshotVos> snapshotVos;

    @Getter
    public static class SnapshotVos {

        @JsonCreator
        public SnapshotVos(@JsonProperty("type") String type,
                           @JsonProperty("updateTime") Long updateTime,
                           @JsonProperty("data") Data data) {
            this.type = type;
            this.updateTime = updateTime;
            this.data = data;
        }

        private final String type;
        private final Long updateTime;
        private final Data data;

        @Getter
        public static class Data {

            @JsonCreator
            public Data(@JsonProperty("totalAssetOfBtc") BigDecimal totalAssetOfBtc,
                        @JsonProperty("balances") List<Balance> balances) {
                this.totalAssetOfBtc = totalAssetOfBtc;
                this.balances = balances;
            }

            private final BigDecimal totalAssetOfBtc;
            private final List<Balance> balances;

            @Getter
            public static class Balance {

                @JsonCreator
                public Balance(@JsonProperty("asset") String asset,
                               @JsonProperty("free") BigDecimal free,
                               @JsonProperty("locked") BigDecimal locked) {
                    this.asset = asset;
                    this.free = free;
                    this.locked = locked;
                }

                private final String asset;
                private final BigDecimal free;
                private final BigDecimal locked;
            }
        }
    }
}
