package com.openjob;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

    /**
     * Sends one turn to the agent.
     *
     * @throws Timeout when the gateway took the turn but did not answer; the
     *         agent may still be working on it
     * @throws Unreachable when no connection could be made, so nothing was sent
     * @throws Rejected when the gateway answered and refused the turn
     */
    String reply(String agent, String message, String conversation) {
        ChatRequest request = new ChatRequest(agent, List.of(new ChatMessage("user", message)), conversation);
        ChatResponse response;
        try {
            response = http.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (ResourceAccessException failure) {
            if (neverConnected(failure)) {
                throw new Unreachable(failure);
            }
            throw new Timeout(failure);
        } catch (RestClientResponseException rejection) {
            throw new Rejected(rejection.getStatusCode().value(), rejection);
        }
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("the gateway returned no reply");
        }
        return response.choices().getFirst().message().content();
    }

    private static boolean neverConnected(ResourceAccessException failure) {
        Throwable cause = failure.getCause();
        return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
    }

    /** The gateway did not answer in time; the turn may still be running. */
    static class Timeout extends RuntimeException {

        Timeout(Throwable cause) {
            super("the agent gateway did not answer in time", cause);
        }
    }

    /** No connection was made, so the turn never reached the agent. */
    static class Unreachable extends RuntimeException {

        Unreachable(Throwable cause) {
            super("the agent gateway did not accept a connection", cause);
        }
    }

    /** The gateway answered and refused the turn. */
    static class Rejected extends RuntimeException {

        private final int status;

        Rejected(int status, Throwable cause) {
            super("the agent gateway refused the turn with HTTP " + status, cause);
            this.status = status;
        }

        int status() {
            return status;
        }
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
