package com.openjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthServiceTest {

    private Path database;
    private JdbcTemplate jdbc;
    private AuthService auth;

    @BeforeEach
    void setUp() throws Exception {
        database = Files.createTempFile("openjob-auth", ".db");
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
        jdbc.execute("""
                CREATE TABLE users (
                    email         TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
        auth = new AuthService(new UserStore(jdbc));
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(database);
    }

    @Test
    void signupStoresANormalizedEmailAndAHash() {
        assertThat(auth.signup(" Me@Example.com ", "correct horse", "1.2.3.4"))
                .isEqualTo("me@example.com");

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?",
                String.class, "me@example.com");
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("correct horse");
    }

    @Test
    void signupRejectsAnEmailThatAlreadyExists() {
        auth.signup("me@example.com", "correct horse", "1.2.3.4");

        assertThatThrownBy(() -> auth.signup("ME@example.com", "another one", "1.2.3.4"))
                .isInstanceOfSatisfying(AuthService.AuthException.class, problem ->
                        assertThat(problem.status).isEqualTo(409));
    }

    @Test
    void loginAcceptsThePasswordAndRejectsEverythingElse() {
        auth.signup("me@example.com", "correct horse", "1.2.3.4");

        assertThat(auth.login(" Me@Example.com", "correct horse", "5.6.7.8"))
                .isEqualTo("me@example.com");
        assertRejected("me@example.com", "wrong horse");
        assertRejected("nobody@example.com", "correct horse");
    }

    @Test
    void signupValidatesEmailAndPassword() {
        assertThatThrownBy(() -> auth.signup("not-an-email", "correct horse", "1.2.3.4"))
                .isInstanceOfSatisfying(AuthService.AuthException.class, problem ->
                        assertThat(problem.code).isEqualTo("INVALID_EMAIL"));
        assertThatThrownBy(() -> auth.signup("me@example.com", "short", "1.2.3.4"))
                .isInstanceOfSatisfying(AuthService.AuthException.class, problem ->
                        assertThat(problem.code).isEqualTo("INVALID_PASSWORD"));
    }

    @Test
    void limitsAttemptsPerClient() {
        auth.signup("me@example.com", "correct horse", "1.2.3.4");

        for (int attempt = 0; attempt < 10; attempt++) {
            assertRejected("me@example.com", "wrong horse");
        }

        assertThatThrownBy(() -> auth.login("me@example.com", "wrong horse", "9.9.9.9"))
                .isInstanceOfSatisfying(AuthService.AuthException.class, problem ->
                        assertThat(problem.status).isEqualTo(429));

        // A different client is unaffected.
        assertThatCode(() -> auth.login("me@example.com", "correct horse", "8.8.8.8"))
                .doesNotThrowAnyException();
    }

    private void assertRejected(String email, String password) {
        assertThatThrownBy(() -> auth.login(email, password, "9.9.9.9"))
                .isInstanceOfSatisfying(AuthService.AuthException.class, problem -> {
                    assertThat(problem.status).isEqualTo(401);
                    assertThat(problem.code).isEqualTo("INVALID_CREDENTIALS");
                });
    }
}
