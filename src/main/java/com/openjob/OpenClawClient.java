package com.openjob;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenClawClient {

    /** A chat turn can take minutes; the cap is only there to free the thread. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    private final RestClient http;

    OpenClawClient(@Value("${OPENCLAW_URL}") String url,
                   @Value("${OPENCLAW_TOKEN}") String token) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build());
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    String reply(String agent, String message, String conversation) {
        ChatRequest request = new ChatRequest(agent, List.of(new ChatMessage("user", message)), conversation);
        ChatResponse response = http.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("the gateway returned no reply");
        }
        return response.choices().getFirst().message().content();
    }

    record ChatRequest(String model, List<ChatMessage> messages, String user) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatResponse(List<Choice> choices) {
    }

    record Choice(ChatMessage message) {
    }
}
