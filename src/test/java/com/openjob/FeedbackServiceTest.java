package com.openjob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {

    private HttpServer telegram;
    private final AtomicInteger telegramStatus = new AtomicInteger(200);
    private final AtomicReference<String> telegramBody = new AtomicReference<>();
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        telegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        telegram.createContext("/bot", exchange -> {
            telegramBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            exchange.sendResponseHeaders(telegramStatus.get(), -1);
            exchange.close();
        });
        telegram.start();
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        telegram.stop(0);
    }

    @Test
    void rejectsBlankFeedback() {
        FeedbackService service = service("bot-token");

        assertThatThrownBy(() -> service.submit("   ", "1.2.3.4"))
                .isInstanceOfSatisfying(FeedbackService.FeedbackException.class, problem -> {
                    assertThat(problem.status).isEqualTo(400);
                    assertThat(problem.code).isEqualTo("INVALID_FEEDBACK");
                });
    }

    @Test
    void rejectsFeedbackOverTheLimit() {
        FeedbackService service = service("bot-token");

        assertThatThrownBy(() -> service.submit("x".repeat(FeedbackService.MAX_MESSAGE + 1), "1.2.3.4"))
                .isInstanceOfSatisfying(FeedbackService.FeedbackException.class,
                        problem -> assertThat(problem.code).isEqualTo("FEEDBACK_TOO_LONG"));
    }

    @Test
    void deliversTheTrimmedMessageToTelegram() {
        FeedbackService service = service("bot-token");

        FeedbackService.Outcome outcome = service.submit("  the board looks wrong  ", "1.2.3.4");

        assertThat(outcome.delivered()).isTrue();
        assertThat(telegramBody.get()).contains("the board looks wrong").doesNotContain("  the");
        assertThat(counter("delivered")).isEqualTo(1.0);
    }

    @Test
    void reportsAFailedDeliveryToTheCaller() {
        telegramStatus.set(500);
        FeedbackService service = service("bot-token");

        assertThatThrownBy(() -> service.submit("will fail", "1.2.3.4"))
                .isInstanceOfSatisfying(FeedbackService.FeedbackException.class, problem -> {
                    assertThat(problem.status).isEqualTo(502);
                    assertThat(problem.code).isEqualTo("FEEDBACK_DELIVERY_FAILED");
                });
        assertThat(counter("failed")).isEqualTo(1.0);
    }

    @Test
    void withoutCredentialsFeedbackIsReportedUnavailable() {
        FeedbackService service = service("");

        assertThatThrownBy(() -> service.submit("no bot configured", "1.2.3.4"))
                .isInstanceOfSatisfying(FeedbackService.FeedbackException.class, problem -> {
                    assertThat(problem.status).isEqualTo(503);
                    assertThat(problem.code).isEqualTo("FEEDBACK_UNAVAILABLE");
                });
        assertThat(telegramBody.get()).isNull();
        assertThat(counter("disabled")).isEqualTo(1.0);
    }

    @Test
    void limitsEachClientToFiveSubmissions() {
        FeedbackService service = service("bot-token");
        for (int attempt = 0; attempt < 5; attempt++) {
            service.submit("report " + attempt, "9.9.9.9");
        }

        assertThatThrownBy(() -> service.submit("one too many", "9.9.9.9"))
                .isInstanceOfSatisfying(FeedbackService.FeedbackException.class, problem -> {
                    assertThat(problem.status).isEqualTo(429);
                    assertThat(problem.retryAfterSeconds).isNotNull().isPositive();
                });
        assertThat(counter("rate_limited")).isEqualTo(1.0);

        // A different client is unaffected.
        assertThat(service.submit("another client", "8.8.8.8").delivered()).isTrue();
    }

    private FeedbackService service(String botToken) {
        return new FeedbackService(registry, botToken, "42",
                "http://127.0.0.1:" + telegram.getAddress().getPort());
    }

    private double counter(String outcome) {
        var counter = registry.find("openjob.feedback").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
