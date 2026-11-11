package com.openjob;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The one rewrite this release needs: the account column used to hold an email
 * address and now holds the anonymous visitor id. SQLite has no conditional
 * column rename, so the check lives here and runs once per database.
 */
@Component
class SchemaMigration {

    SchemaMigration(JdbcTemplate jdbc) {
        boolean hasEmail = jdbc.query("PRAGMA table_info(user_agents)",
                        (rs, row) -> rs.getString("name"))
                .contains("email");
        if (hasEmail) {
            jdbc.execute("ALTER TABLE user_agents RENAME COLUMN email TO visitor_id");
        }
    }
}
