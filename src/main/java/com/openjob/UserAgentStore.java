package com.openjob;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The visitor's OpenClaw agent. The visitor id is the account id everywhere in
 * the app; the slug is only what the gateway and the workspace are named after.
 */
@Component
class UserAgentStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    UserAgentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the visitor's agent slug, creating it on first use. */
    String ensure(String visitor) {
        String existing = slugFor(visitor);
        if (existing != null) {
            return existing;
        }
        jdbc.update("""
                INSERT INTO user_agents (slug, visitor_id) VALUES (?, ?)
                ON CONFLICT (visitor_id) DO NOTHING
                """, newSlug(), visitor);
        return slugFor(visitor);
    }

    String slugFor(String visitor) {
        List<String> rows = jdbc.query("SELECT slug FROM user_agents WHERE visitor_id = ?",
                (rs, row) -> rs.getString(1), visitor);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    String visitorFor(String slug) {
        List<String> rows = jdbc.query("SELECT visitor_id FROM user_agents WHERE slug = ?",
                (rs, row) -> rs.getString(1), slug);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String newSlug() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return "u-" + HexFormat.of().formatHex(bytes);
    }
}
