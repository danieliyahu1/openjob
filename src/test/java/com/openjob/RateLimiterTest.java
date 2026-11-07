package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(10));

    @Test
    void allowsTheLimitThenReportsHowLongToWait() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(limiter.permit("1.2.3.4")).isZero();
        }

        assertThat(limiter.permit("1.2.3.4")).isPositive();
    }

    @Test
    void keepsClientsApart() {
        limiter.permit("1.2.3.4");
        limiter.permit("1.2.3.4");
        limiter.permit("1.2.3.4");

        assertThat(limiter.permit("5.6.7.8")).isZero();
    }

    @Test
    void aMissingClientSharesOneBucket() {
        limiter.permit(null);
        limiter.permit(null);
        limiter.permit(null);

        assertThat(limiter.permit(null)).isPositive();
    }

    @Test
    void resetForgetsTheClientsAttempts() {
        limiter.permit("1.2.3.4");
        limiter.permit("1.2.3.4");
        limiter.permit("1.2.3.4");

        limiter.reset("1.2.3.4");

        assertThat(limiter.permit("1.2.3.4")).isZero();
    }
}
