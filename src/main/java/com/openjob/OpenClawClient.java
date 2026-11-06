package com.openjob;

import java.net.http.HttpClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class OpenClawClient {

    private final RestClient http;
    private final String agent;

    OpenClawClient(@Value("${OPENCLAW_URL:http://127.0.0.1:18789}") String url,
                   @Value("${OPENCLAW_TOKEN:}") String token,
                   @Value("${OPENCLAW_AGENT:openclaw/main}") String agent) {
        this.http = RestClient.builder()
                // The gateway's HTTP/WS multiplexer rejects HTTP/2 upgrade attempts.
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        this.agent = agent;
    }

    String reply(String message) {
        ChatRequest request = new ChatRequest(agent, List.of(new ChatMessage("user", message)));
        ChatResponse response = http.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
        return response.choices().getFirst().message().content();
    }

    record ChatRequest(String model, List<ChatMessage> messages) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatResponse(List<Choice> choices) {
    }

    record Choice(ChatMessage message) {
    }
}
