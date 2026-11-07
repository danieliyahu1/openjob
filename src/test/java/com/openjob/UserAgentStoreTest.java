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
                    email      TEXT NOT NULL UNIQUE,
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
    void createsOneSlugPerEmail() {
        String slug = store.ensure("alice@example.com");

        assertThat(slug).matches("u-[0-9a-f]{12}");
        assertThat(store.ensure("alice@example.com")).isEqualTo(slug);
        assertThat(store.slugFor("alice@example.com")).isEqualTo(slug);
        assertThat(store.emailFor(slug)).isEqualTo("alice@example.com");
    }

    @Test
    void keepsAccountsApart() {
        assertThat(store.ensure("alice@example.com"))
                .isNotEqualTo(store.ensure("bob@example.com"));
        assertThat(store.emailFor("u-000000000000")).isNull();
    }

    @Test
    void listsEverySlug() {
        String alice = store.ensure("alice@example.com");
        String bob = store.ensure("bob@example.com");

        assertThat(store.slugs()).containsExactlyInAnyOrder(alice, bob);
    }
}
