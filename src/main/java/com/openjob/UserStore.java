package com.openjob;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class UserStore {

    private final JdbcTemplate jdbc;

    UserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    String hash(String email) {
        List<String> rows = jdbc.query("SELECT password_hash FROM users WHERE email = ?",
                (rs, row) -> rs.getString(1), email);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    boolean exists(String email) {
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class, email);
        return found != null && found > 0;
    }

    /** Returns false when the email already has an account. */
    boolean create(String email, String hash) {
        return jdbc.update("""
                INSERT INTO users (email, password_hash)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = ?)
                """, email, hash, email) == 1;
    }
}
