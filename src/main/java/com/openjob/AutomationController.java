package com.openjob;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The user creates automations from the app; the agent never needs its tool.
 * Every call is resolved to the visitor's own agent.
 */
@RestController
class AutomationController {

    private final AutomationsClient automations;
    private final UserAgentStore userAgents;
    private final AgentProvisioning provisioning;

    AutomationController(AutomationsClient automations, UserAgentStore userAgents,
                         AgentProvisioning provisioning) {
        this.automations = automations;
        this.userAgents = userAgents;
        this.provisioning = provisioning;
    }

    @GetMapping("/api/automations")
    List<AutomationsClient.Job> list(HttpServletRequest request) {
        return automations.list(readyAgent(request));
    }

    @PostMapping("/api/automations")
    AutomationsClient.Job create(@RequestParam String name,
                                 @RequestParam String prompt,
                                 @RequestParam(defaultValue = "daily") String repeat,
                                 @RequestParam(required = false) String time,
                                 @RequestParam(required = false) String date,
                                 @RequestParam(defaultValue = "0") int day,
                                 @RequestParam(defaultValue = "UTC") String timezone,
                                 HttpServletRequest request) {
        String agent = readyAgent(request);
        String jobName = name == null ? "" : name.strip();
        String message = prompt == null ? "" : prompt.strip();
        if (jobName.isEmpty() || message.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and prompt are required");
        }
        if (jobName.length() > 100 || message.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name or prompt is too long");
        }
        return automations.add(agent, jobName, schedule(repeat, time, date, day, timezone), message);
    }

    @PatchMapping("/api/automations/{id}")
    void setEnabled(@PathVariable String id, @RequestParam boolean enabled,
                    HttpServletRequest request) {
        owned(id, request);
        automations.setEnabled(id, enabled);
    }

    @PostMapping("/api/automations/{id}/run")
    void run(@PathVariable String id, HttpServletRequest request) {
        owned(id, request);
        automations.run(id);
    }

    @DeleteMapping("/api/automations/{id}")
    void remove(@PathVariable String id, HttpServletRequest request) {
        owned(id, request);
        automations.remove(id);
    }

    /** The three shapes the form offers: daily, weekly, once. */
    static Map<String, Object> schedule(String repeat, String time, String date, int day,
                                        String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (RuntimeException unknownZone) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown timezone");
        }

        if ("once".equals(repeat)) {
            if (date == null || date.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date and time are required");
            }
            try {
                String at = LocalDateTime.parse(date).atZone(zone).toInstant().toString();
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("kind", "at");
                spec.put("at", at);
                spec.put("tz", zone.getId());
                return spec;
            } catch (DateTimeParseException badDate) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid date and time");
            }
        }

        int hour;
        int minute;
        try {
            String[] parts = time.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (RuntimeException badTime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time is required");
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid time");
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("kind", "cron");
        if ("weekly".equals(repeat)) {
            int weekday = day >= 0 && day <= 6 ? day : ZonedDateTime.now(zone).getDayOfWeek().getValue() % 7;
            spec.put("expr", minute + " " + hour + " * * " + weekday);
        } else {
            spec.put("expr", minute + " " + hour + " * * *");
        }
        spec.put("tz", zone.getId());
        return spec;
    }

    private String agent(HttpServletRequest request) {
        String user = VisitorIdentity.require(request);
        return userAgents.ensure(user);
    }

    /**
     * The slug, with the agent and workspace guaranteed to exist before a call.
     * Provisioning is remembered per process, so this is free after the first
     * request for an account.
     */
    private String readyAgent(HttpServletRequest request) {
        String agent = agent(request);
        try {
            provisioning.ensure(agent);
        } catch (AgentProvisioning.AgentUnavailable notReady) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "your agent is not ready yet, so nothing was changed");
        }
        return agent;
    }

    /**
     * A job is only visible to the account that owns its agent. Authorizing a
     * toggle or a delete must not depend on the provisioner being up, so this
     * uses the stored slug only.
     */
    private void owned(String id, HttpServletRequest request) {
        String agent = agent(request);
        AutomationsClient.Job job = automations.get(id);
        if (job.agentId() == null || !job.agentId().equals(agent)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @ExceptionHandler(AutomationsClient.JobNotFoundException.class)
    ResponseEntity<Void> missing() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** The gateway did not complete the call; that is not a missing job. */
    @ExceptionHandler(AutomationsClient.GatewayException.class)
    ResponseEntity<Map<String, String>> gatewayDown(AutomationsClient.GatewayException problem) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "GATEWAY_UNAVAILABLE",
                        "message", problem.unreachable()
                                ? "the scheduler is not reachable, so nothing was changed"
                                : "the scheduler refused the request; check the automation details"));
    }
}
