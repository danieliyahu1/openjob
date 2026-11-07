package com.openjob;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit per client key, in memory. One replica, so memory is
 * enough; a restart forgets every window.
 */
final class RateLimiter {

    private static final String UNKNOWN = "unknown";
    private static final int MAX_KEYS = 1000;

    private final int limit;
    private final long windowMillis;

    /** client key -> timestamps of the attempts inside the window. */
    private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
    }

    /**
     * Records one attempt for the client and returns 0 when it is allowed, or
     * the seconds to wait when the limit is already reached.
     */
    long permit(String client) {
        String key = key(client);
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;
        ArrayDeque<Long> attempts = hits.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < windowStart) {
                attempts.removeFirst();
            }
            if (attempts.size() >= limit) {
                long oldest = attempts.peekFirst() == null ? now : attempts.peekFirst();
                return Math.max(1, (oldest + windowMillis - now + 999) / 1000);
            }
            attempts.addLast(now);
        }
        if (hits.size() > MAX_KEYS) {
            hits.entrySet().removeIf(entry -> {
                ArrayDeque<Long> queue = entry.getValue();
                synchronized (queue) {
                    return queue.isEmpty() || queue.peekLast() < windowStart;
                }
            });
        }
        return 0;
    }

    /** Forgets a client's attempts, e.g. after a successful login. */
    void reset(String client) {
        hits.remove(key(client));
    }

    private static String key(String client) {
        return client == null || client.isBlank() ? UNKNOWN : client;
    }
}
