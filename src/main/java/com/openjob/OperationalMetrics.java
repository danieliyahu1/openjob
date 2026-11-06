package com.openjob;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Operational signals for the parts of the app that do real work: agent calls,
 * the gateway they depend on, and the board the agent writes to.
 *
 * <p>Every label is bounded. No session ids, user ids or URLs are exposed.
 */
@Component
class OperationalMetrics {

    private static final List<String> STATUSES = List.of("found", "applied", "blocked");

    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;
    private final String databasePath;
    private final String gatewayHost;
    private final int gatewayPort;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger gatewayUp = new AtomicInteger();
    private final AtomicLong lastSuccessMillis = new AtomicLong();

    OperationalMetrics(MeterRegistry registry, JdbcTemplate jdbc,
                       @Value("${OPENCLAW_URL:http://127.0.0.1:18789}") String gatewayUrl,
                       @Value("${spring.datasource.url}") String datasourceUrl) {
        this.registry = registry;
        this.jdbc = jdbc;

        URI uri = URI.create(gatewayUrl);
        this.gatewayHost = uri.getHost();
        this.gatewayPort = uri.getPort() > 0 ? uri.getPort() : 18789;
        this.databasePath = localDatabasePath(datasourceUrl);

        Gauge.builder("openjob.agent.requests.in.flight", inFlight, AtomicInteger::doubleValue)
                .description("Agent calls currently waiting on the OpenClaw gateway")
                .register(registry);
        Gauge.builder("openjob.openclaw.up", gatewayUp, AtomicInteger::doubleValue)
                .description("1 when the OpenClaw gateway accepts TCP connections")
                .register(registry);
        Gauge.builder("openjob.agent.last.success.timestamp", lastSuccessMillis,
                        value -> value.get() / 1000.0)
                .description("Unix timestamp of the last successful agent call")
                .register(registry);
        Gauge.builder("openjob.db.size.bytes", this, OperationalMetrics::databaseSize)
                .description("Size of the SQLite database file")
                .register(registry);
        for (String status : STATUSES) {
            Gauge.builder("openjob.applications", this, metrics -> metrics.count(status))
                    .description("Applications on the board by status")
                    .tag("status", status)
                    .register(registry);
        }
    }

    void agentStarted() {
        inFlight.incrementAndGet();
    }

    void agentFinished(String outcome, long nanos) {
        inFlight.decrementAndGet();
        registry.counter("openjob.agent.requests", "outcome", outcome).increment();
        Timer.builder("openjob.agent.request.duration")
                .description("Time spent waiting on the OpenClaw gateway")
                .tag("outcome", outcome)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        if ("success".equals(outcome)) {
            lastSuccessMillis.set(System.currentTimeMillis());
        }
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 15_000)
    void probeGateway() {
        boolean up = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(gatewayHost, gatewayPort), 2_000);
            up = true;
        } catch (Exception reachable) {
            up = false;
        }
        gatewayUp.set(up ? 1 : 0);
    }

    private double count(String status) {
        try {
            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM applications WHERE status = ?", Integer.class, status);
            return rows == null ? 0 : rows;
        } catch (RuntimeException notReady) {
            return 0;
        }
    }

    private double databaseSize() {
        if (databasePath == null) {
            return 0;
        }
        try {
            return Files.size(Path.of(databasePath));
        } catch (Exception missing) {
            return 0;
        }
    }

    private static String localDatabasePath(String datasourceUrl) {
        String path = datasourceUrl.replaceFirst("^jdbc:sqlite:", "");
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.isBlank() || path.startsWith(":memory:") ? null : path;
    }
}
