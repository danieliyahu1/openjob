package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ApplicationStoreTest {

    private Path database;
    private ApplicationStore store;

    @BeforeEach
    void setUp() throws Exception {
        database = Files.createTempFile("openjob-board", ".db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + database);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        store = new ApplicationStore(new JdbcTemplate(dataSource));
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(database);
    }

    @Test
    void theSamePostingIsStoredOncePerAccount() {
        long first = store.add("me@example.com", "Acme", "Backend", "https://acme.example/1");
        long again = store.add("me@example.com", "Acme", "Backend", "https://acme.example/1");

        assertThat(again).isEqualTo(first);
        assertThat(store.list("me@example.com", null)).hasSize(1);
    }

    @Test
    void twoAccountsMaySaveTheSamePosting() {
        long mine = store.add("me@example.com", "Acme", "Backend", "https://acme.example/1");
        long yours = store.add("you@example.com", "Acme", "Backend", "https://acme.example/1");

        assertThat(yours).isNotEqualTo(mine);
        assertThat(store.list("you@example.com", null)).hasSize(1);
    }

    @Test
    void postingsWithoutAUrlAreNotDeduplicated() {
        long first = store.add("me@example.com", "Acme", "Backend", null);
        long second = store.add("me@example.com", "Acme", "Backend", null);

        assertThat(second).isNotEqualTo(first);
        assertThat(store.list("me@example.com", null)).hasSize(2);
    }
}
