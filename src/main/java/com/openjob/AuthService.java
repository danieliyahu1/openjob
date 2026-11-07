package com.openjob;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Signup and login over the users table. The email is the username; passwords
 * are stored as BCrypt hashes. Attempts are limited per client IP, in memory.
 */
@Component
class AuthService {

    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 64;
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final UserStore users;
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    /** Compared against when the email is unknown, so both failures cost the same. */
    private final String decoy = encoder.encode("openjob-decoy-" + System.nanoTime());

    /** client key -> timestamps of attempts. One replica, so memory is enough. */
    private final Map<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    AuthService(UserStore users) {
        this.users = users;
    }

    static class AuthException extends RuntimeException {

        final int status;
        final String code;

        AuthException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    String signup(String email, String password, String client) {
        String user = email(email);
        password(password);
        long wait = permit(client);
        if (wait > 0) {
            throw new AuthException(429, "RATE_LIMITED",
                    "Too many attempts; try again in " + wait + " seconds");
        }
        if (!users.create(user, encoder.encode(password))) {
            throw new AuthException(409, "EMAIL_TAKEN", "That email already has an account");
        }
        return user;
    }

    String login(String email, String password, String client) {
        long wait = permit(client);
        if (wait > 0) {
            throw new AuthException(429, "RATE_LIMITED",
                    "Too many attempts; try again in " + wait + " seconds");
        }
        String user = normalize(email);
        String hash = user == null ? null : users.hash(user);
        // An empty password can never match a stored hash (signup needs 8), and
        // the decoy makes an unknown email cost the same as a wrong password.
        String attempt = password == null || password.isEmpty() ? "invalid" : password;
        if (hash == null || !encoder.matches(attempt, hash)) {
            if (hash == null) {
                encoder.matches(attempt, decoy);
            }
            throw new AuthException(401, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        attempts.remove(client == null ? "unknown" : client);
        return user;
    }

    private static String normalize(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 254 && email.matches(EMAIL) ? email : null;
    }

    private static String email(String value) {
        String email = normalize(value);
        if (email == null) {
            throw new AuthException(400, "INVALID_EMAIL", "Enter a valid email address");
        }
        return email;
    }

    private static void password(String value) {
        if (value == null || value.length() < MIN_PASSWORD || value.length() > MAX_PASSWORD) {
            throw new AuthException(400, "INVALID_PASSWORD",
                    "Password must be " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters");
        }
    }

    /** Records one attempt for the client and returns 0, or the seconds to wait. */
    private long permit(String client) {
        String key = client == null ? "unknown" : client;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;
        ArrayDeque<Long> hits = attempts.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.removeFirst();
            }
            if (hits.size() >= MAX_ATTEMPTS) {
                long oldest = hits.peekFirst() == null ? now : hits.peekFirst();
                return Math.max(1, (oldest + WINDOW_MILLIS - now + 999) / 1000);
            }
            hits.addLast(now);
        }
        if (attempts.size() > 1000) {
            attempts.entrySet().removeIf(entry -> {
                ArrayDeque<Long> queue = entry.getValue();
                synchronized (queue) {
                    return queue.isEmpty() || queue.peekLast() < windowStart;
                }
            });
        }
        return 0;
    }
}
