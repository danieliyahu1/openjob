package com.openjob;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ApplicationStore {

    private final JdbcTemplate jdbc;

    ApplicationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Job(long id, String company, String title, String url, String status) {
    }

    List<Job> list(String userId, String status) {
        String sql = "SELECT id, company, title, url, status FROM applications WHERE user_id = ?";
        return status == null
                ? jdbc.query(sql + " ORDER BY id DESC", this::map, userId)
                : jdbc.query(sql + " AND status = ? ORDER BY id DESC", this::map, userId, status);
    }

    long add(String userId, String company, String title, String url) {
        List<Long> inserted = jdbc.query("""
                INSERT INTO applications (user_id, company, title, url)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, url) DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getLong(1), userId, company, title, url);
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        return jdbc.queryForObject(
                "SELECT id FROM applications WHERE user_id = ? AND url = ?", Long.class, userId, url);
    }

    int mark(String userId, long id, String status) {
        return jdbc.update(
                "UPDATE applications SET status = ?, updated_at = datetime('now') WHERE id = ? AND user_id = ?",
                status, id, userId);
    }

    String criteria(String userId) {
        List<String> rows = jdbc.query(
                "SELECT text FROM criteria WHERE user_id = ?",
                (rs, row) -> rs.getString("text"), userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void saveCriteria(String userId, String text) {
        jdbc.update("""
                INSERT INTO criteria (user_id, text) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET text = excluded.text, updated_at = datetime('now')
                """, userId, text);
    }

    private Job map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Job(rs.getLong("id"), rs.getString("company"), rs.getString("title"),
                rs.getString("url"), rs.getString("status"));
    }
}
