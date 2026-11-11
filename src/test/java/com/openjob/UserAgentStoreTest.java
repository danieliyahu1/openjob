package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class UserAgentStoreTest {

    private Path database;
    private UserAgentStore store;

    @BeforeEach
    void setUp() throws Exception {
        database = Files.createTempFile("openjob-agents", ".db");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
        jdbc.execute("""
                CREATE TABLE user_agents (
                    slug       TEXT PRIMARY KEY,
                    visitor_id TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
        store = new UserAgentStore(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(database);
    }

    @Test
    void createsOneSlugPerVisitor() {
        String slug = store.ensure("a-000000000000000000000001");

        assertThat(slug).matches("u-[0-9a-f]{12}");
        assertThat(store.ensure("a-000000000000000000000001")).isEqualTo(slug);
        assertThat(store.slugFor("a-000000000000000000000001")).isEqualTo(slug);
        assertThat(store.visitorFor(slug)).isEqualTo("a-000000000000000000000001");
    }

    @Test
    void keepsVisitorsApart() {
        assertThat(store.ensure("a-000000000000000000000001"))
                .isNotEqualTo(store.ensure("a-000000000000000000000002"));
        assertThat(store.visitorFor("u-000000000000")).isNull();
    }
}
