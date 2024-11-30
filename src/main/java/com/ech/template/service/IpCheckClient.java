package com.ech.template.service;

import com.ech.template.model.IpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Log4j2
@RequiredArgsConstructor
public class IpCheckClient {

    private static final String IP_CHECK_URL = "https://api.ipify.org/?format=json";
    private static final String NA = "N/A";

    private final ObjectMapper objectMapper;

    public String getMyIp() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IP_CHECK_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("IP response: {}: {}", response.statusCode(), response.body());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), IpResponse.class).getIp();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error while retrieving IP", e);
            return NA;
        }

        return NA;
    }
}
