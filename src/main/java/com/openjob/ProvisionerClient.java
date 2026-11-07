package com.openjob;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Asks the sidecar in the OpenClaw pod to create a user's agent and workspace.
 * Idempotent: the sidecar checks first and returns immediately when they exist.
 */
@Component
class ProvisionerClient {

    private final RestClient http;

    ProvisionerClient(@Value("${PROVISIONER_URL:http://127.0.0.1:18790}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().requestFactory(factory).baseUrl(url).build();
    }

    void ensure(String slug) {
        http.post()
                .uri("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("slug", slug))
                .retrieve()
                .toBodilessEntity();
    }
}
