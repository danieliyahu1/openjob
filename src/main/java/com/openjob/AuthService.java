package com.openjob;

import java.time.Duration;
import java.util.Locale;
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
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final UserStore users;
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    /** Compared against when the email is unknown, so both failures cost the same. */
    private final String decoy = encoder.encode("openjob-decoy-" + System.nanoTime());

    private final RateLimiter attempts = new RateLimiter(10, Duration.ofMinutes(15));

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

    /**
     * Validates the address and password and applies the per-client limit,
     * then returns the normalized email. Nothing is written yet, so the
     * account can be created only after its agent exists.
     *
     * @throws AuthException 400 for bad input, 429 when the client is limited,
     *         409 when the email is taken
     */
    String verifySignup(String email, String password, String client) {
        String user = email(email);
        password(password);
        long wait = attempts.permit(client);
        if (wait > 0) {
            throw new AuthException(429, "RATE_LIMITED",
                    "Too many attempts; try again in " + wait + " seconds");
        }
        if (users.exists(user)) {
            throw taken();
        }
        return user;
    }

    /**
     * Writes the account after its agent is in place.
     *
     * @throws AuthException 409 when another signup took the email meanwhile
     */
    void create(String email, String password) {
        if (!users.create(email, encoder.encode(password))) {
            throw taken();
        }
    }

    private static AuthException taken() {
        return new AuthException(409, "EMAIL_TAKEN", "That email already has an account");
    }

    String login(String email, String password, String client) {
        long wait = attempts.permit(client);
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
        attempts.reset(client);
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
}
