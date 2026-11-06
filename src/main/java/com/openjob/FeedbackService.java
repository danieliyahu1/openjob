package com.openjob;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anonymous user feedback.
 *
 * <p>The browser sends only a message. This service validates it, rate-limits
 * per client, and forwards it to a private Telegram chat. Delivery is
 * best-effort: nothing is stored, so a Telegram failure is reported back to the
 * page instead of being retried later.
 *
 * <p>The bot token and chat id are runtime configuration and never reach the
 * browser. The feedback text is never logged.
 */
@Component
class FeedbackService {

    static final int MAX_MESSAGE = 1500;
    private static final int RATE_LIMIT = 5;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final MeterRegistry registry;
    private final RestClient http;
    private final String botToken;
    private final String chatId;
    private final String endpoint;

    /** client key -> timestamps of accepted submissions. One replica, so memory is enough. */
    private final Map<String, ArrayDeque<Long>> submissions = new ConcurrentHashMap<>();

    record Outcome(boolean delivered) {
    }

    static class FeedbackException extends RuntimeException {

        final int status;
        final String code;
        final Long retryAfterSeconds;

        FeedbackException(int status, String code, String message, Long retryAfterSeconds) {
            super(message);
            this.status = status;
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    FeedbackService(MeterRegistry registry,
                    @Value("${TELEGRAM_FEEDBACK_BOT_TOKEN:}") String botToken,
                    @Value("${TELEGRAM_FEEDBACK_CHAT_ID:}") String chatId,
                    @Value("${FEEDBACK_TELEGRAM_SEND_URL:https://api.telegram.org}") String telegramBaseUrl) {
        this.registry = registry;
        this.botToken = botToken == null ? "" : botToken.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
        this.endpoint = telegramBaseUrl.replaceAll("/+$", "") + "/bot" + this.botToken + "/sendMessage";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** True when both Telegram settings are present. */
    boolean enabled() {
        return !botToken.isEmpty() && !chatId.isEmpty();
    }

    Outcome submit(String message, String clientIp) {
        String text = validate(message);
        long retryAfter = permit(clientIp == null ? "unknown" : clientIp);
        if (retryAfter > 0) {
            count("rate_limited");
            throw new FeedbackException(429, "RATE_LIMITED",
                    "Too many submissions; please wait a bit before sending more feedback", retryAfter);
        }
        if (!enabled()) {
            count("disabled");
            log.warn("feedback_delivery_disabled");
            throw new FeedbackException(503, "FEEDBACK_UNAVAILABLE",
                    "Feedback is not available right now", null);
        }
        long started = System.nanoTime();
        try {
            http.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", format(text),
                            "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
            count("delivered");
            timer("delivered", System.nanoTime() - started);
            log.info("feedback_delivered");
            return new Outcome(true);
        } catch (RuntimeException failure) {
            count("failed");
            timer("failed", System.nanoTime() - started);
            log.warn("feedback_delivery_failed error={}", failure.getClass().getSimpleName());
            throw new FeedbackException(502, "FEEDBACK_DELIVERY_FAILED",
                    "Could not send feedback; please try again", null);
        }
    }

    private static String format(String message) {
        return "New OpenJob feedback:\n\n" + message;
    }

    private static String validate(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            throw new FeedbackException(400, "INVALID_FEEDBACK", "Feedback message is required", null);
        }
        if (text.length() > MAX_MESSAGE) {
            throw new FeedbackException(400, "FEEDBACK_TOO_LONG",
                    "Feedback must be at most " + MAX_MESSAGE + " characters", null);
        }
        return text;
    }

    /**
     * Records one submission for the client and returns 0 when it is allowed,
     * or the seconds to wait when it is not.
     */
    private long permit(String client) {
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_WINDOW.toMillis();
        ArrayDeque<Long> hits = submissions.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.removeFirst();
            }
            if (hits.size() >= RATE_LIMIT) {
                long oldest = hits.peekFirst() == null ? now : hits.peekFirst();
                long waitMillis = oldest + RATE_WINDOW.toMillis() - now;
                return Math.max(1, (waitMillis + 999) / 1000);
            }
            hits.addLast(now);
        }
        if (submissions.size() > 1000) {
            submissions.entrySet().removeIf(entry -> {
                ArrayDeque<Long> queue = entry.getValue();
                synchronized (queue) {
                    return queue.isEmpty() || queue.peekLast() < windowStart;
                }
            });
        }
        return 0;
    }

    private void count(String outcome) {
        registry.counter("openjob.feedback", "outcome", outcome.toLowerCase(Locale.ROOT)).increment();
    }

    private void timer(String outcome, long nanos) {
        Timer.builder("openjob.feedback.delivery")
                .description("Time spent delivering feedback to Telegram")
                .tag("outcome", outcome)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
