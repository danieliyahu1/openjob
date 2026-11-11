package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** An existing volume keeps its agents when the account column changes. */
class SchemaMigrationTest {

    private Path database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        database = Files.createTempFile("openjob-migration", ".db");
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(database);
    }

    @Test
    void renamesTheOldEmailColumnAndKeepsItsRows() {
        jdbc.execute("""
                CREATE TABLE user_agents (
                    slug       TEXT PRIMARY KEY,
                    email      TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
        jdbc.update("INSERT INTO user_agents (slug, email) VALUES (?, ?)",
                "u-0123456789ab", "me@example.com");

        new SchemaMigration(jdbc);

        assertThat(columns()).contains("visitor_id").doesNotContain("email");
        String visitor = jdbc.queryForObject(
                "SELECT visitor_id FROM user_agents WHERE slug = ?", String.class, "u-0123456789ab");
        assertThat(visitor).isEqualTo("me@example.com");
    }

    @Test
    void leavesAFreshTableAlone() {
        jdbc.execute("CREATE TABLE user_agents (slug TEXT PRIMARY KEY, visitor_id TEXT NOT NULL UNIQUE)");

        new SchemaMigration(jdbc);

        assertThat(columns()).containsExactly("slug", "visitor_id");
    }

    private List<String> columns() {
        return jdbc.query("PRAGMA table_info(user_agents)", (rs, row) -> rs.getString("name"));
    }
}
