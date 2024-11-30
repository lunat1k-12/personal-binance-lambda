package com.ech.template.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IpResponse {

    @JsonCreator
    public IpResponse(@JsonProperty("ip") String ip) {
        this.ip = ip;
    }

    private String ip;
}
