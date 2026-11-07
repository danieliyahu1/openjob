package com.openjob;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenClaw's scheduler owns the jobs. This app only asks the gateway to add,
 * change, run, or remove them through the bundled admin HTTP RPC plugin.
 */
@Component
class AutomationsClient {

    private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday"};
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient http;

    AutomationsClient(@Value("${OPENCLAW_URL}") String url,
                      @Value("${OPENCLAW_TOKEN}") String token) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build());
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    List<Job> list(String agent) {
        JsonNode page = call("cron.list", Map.of("agentId", agent, "includeDisabled", true));
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : page.path("jobs")) {
            jobs.add(job(node));
        }
        return jobs;
    }

    Job get(String id) {
        return job(call("cron.get", Map.of("id", id)));
    }

    Job add(String agent, String name, Map<String, Object> schedule, String prompt) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("agentId", agent);
        params.put("schedule", schedule);
        params.put("sessionTarget", "isolated");
        params.put("wakeMode", "now");
        params.put("payload", Map.of("kind", "agentTurn", "message", prompt));
        params.put("enabled", true);
        return job(call("cron.add", params));
    }

    void setEnabled(String id, boolean enabled) {
        call("cron.update", Map.of("id", id, "patch", Map.of("enabled", enabled)));
    }

    void run(String id) {
        call("cron.run", Map.of("id", id, "mode", "force"));
    }

    void remove(String id) {
        call("cron.remove", Map.of("id", id));
    }

    private JsonNode call(String method, Map<String, Object> params) {
        RpcResponse response;
        try {
            response = http.post()
                    .uri("/api/v1/admin/rpc")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("method", method, "params", params))
                    .retrieve()
                    .body(RpcResponse.class);
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) {
                throw new JobNotFoundException();
            }
            throw new GatewayException(
                    "gateway rejected " + method + ": " + failure.getStatusCode(), failure, false);
        } catch (ResourceAccessException unreachable) {
            throw new GatewayException("gateway unreachable for " + method, unreachable, true);
        }
        if (response == null || !response.ok()) {
            String code = response == null || response.error() == null
                    ? "" : response.error().code();
            if ("not_found".equalsIgnoreCase(code)) {
                throw new JobNotFoundException();
            }
            String message = response == null || response.error() == null
                    ? "no response" : response.error().message();
            throw new GatewayException("gateway rejected " + method + ": " + message, null, false);
        }
        return response.payload();
    }

    /** The gateway could not complete the call; this is not a missing job. */
    static class GatewayException extends RuntimeException {

        private final boolean unreachable;

        GatewayException(String message, Throwable cause, boolean unreachable) {
            super(message, cause);
            this.unreachable = unreachable;
        }

        /** True when no answer arrived at all, false when the gateway refused. */
        boolean unreachable() {
            return unreachable;
        }
    }

    /** The gateway answered, and the job does not exist. */
    static class JobNotFoundException extends RuntimeException {
    }

    private static Job job(JsonNode node) {
        JsonNode schedule = node.path("schedule");
        return new Job(
                node.path("id").asText(null),
                node.path("name").asText(""),
                node.path("enabled").asBoolean(true),
                schedule,
                describe(schedule),
                node.hasNonNull("nextRunAtMs") ? node.get("nextRunAtMs").asLong() : null,
                node.path("lastRunStatus").asText(null),
                node.path("agentId").asText(null));
    }

    /** Plain-language rendering of the small schedule shapes this app creates. */
    static String describe(JsonNode schedule) {
        String kind = schedule.path("kind").asText("");
        if ("at".equals(kind)) {
            try {
                return "Once on " + Instant.parse(schedule.path("at").asText())
                        .atZone(zoneOf(schedule))
                        .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"));
            } catch (RuntimeException notIso) {
                return "Once";
            }
        }
        if ("every".equals(kind)) {
            long minutes = Math.max(1, schedule.path("everyMs").asLong(0) / 60_000);
            return "Every " + minutes + " min";
        }
        if ("cron".equals(kind)) {
            String[] fields = schedule.path("expr").asText("").trim().split("\\s+");
            if (fields.length == 5 && "*".equals(fields[2]) && "*".equals(fields[3])) {
                String at = clock(fields[1], fields[0]);
                if ("*".equals(fields[4])) {
                    return "Every day at " + at;
                }
                int day;
                try {
                    day = Integer.parseInt(fields[4]);
                } catch (NumberFormatException notSimple) {
                    return "Cron " + schedule.path("expr").asText();
                }
                if (day >= 0 && day < DAYS.length) {
                    return "Every " + DAYS[day] + " at " + at;
                }
            }
            return "Cron " + schedule.path("expr").asText();
        }
        return "Scheduled";
    }

    private static String clock(String hour, String minute) {
        try {
            return String.format("%02d:%02d", Integer.parseInt(hour), Integer.parseInt(minute));
        } catch (NumberFormatException notSimple) {
            return hour + ":" + minute;
        }
    }

    /** The job's own timezone, so a user abroad still sees the time they picked. */
    private static ZoneId zoneOf(JsonNode schedule) {
        try {
            return ZoneId.of(schedule.path("tz").asText());
        } catch (RuntimeException unknownZone) {
            return ZoneId.systemDefault();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RpcResponse(String id, boolean ok, JsonNode payload, RpcError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RpcError(String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(String id, String name, boolean enabled, JsonNode schedule,
               String scheduleText, Long nextRunAtMs, String lastRunStatus, String agentId) {
    }
}
