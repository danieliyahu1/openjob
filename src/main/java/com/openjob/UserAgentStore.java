package com.openjob;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The account's OpenClaw agent. The email stays the user id everywhere in the
 * app; the slug is only what the gateway and the workspace are named after.
 */
@Component
class UserAgentStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    UserAgentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the account's agent slug, creating it on first use. */
    String ensure(String email) {
        String existing = slugFor(email);
        if (existing != null) {
            return existing;
        }
        jdbc.update("""
                INSERT INTO user_agents (slug, email) VALUES (?, ?)
                ON CONFLICT (email) DO NOTHING
                """, newSlug(), email);
        return slugFor(email);
    }

    String slugFor(String email) {
        List<String> rows = jdbc.query("SELECT slug FROM user_agents WHERE email = ?",
                (rs, row) -> rs.getString(1), email);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    String emailFor(String slug) {
        List<String> rows = jdbc.query("SELECT email FROM user_agents WHERE slug = ?",
                (rs, row) -> rs.getString(1), slug);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String newSlug() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return "u-" + HexFormat.of().formatHex(bytes);
    }
}
