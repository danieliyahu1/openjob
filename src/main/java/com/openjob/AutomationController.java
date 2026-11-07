package com.openjob;

import jakarta.servlet.http.HttpSession;
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
 * Every call is resolved to the signed-in account's own agent.
 */
@RestController
class AutomationController {

    private final AutomationsClient automations;
    private final UserAgentStore userAgents;
    private final ProvisionerClient provisioner;

    AutomationController(AutomationsClient automations, UserAgentStore userAgents,
                         ProvisionerClient provisioner) {
        this.automations = automations;
        this.userAgents = userAgents;
        this.provisioner = provisioner;
    }

    @GetMapping("/api/automations")
    List<AutomationsClient.Job> list(HttpSession session) {
        return automations.list(readyAgent(session));
    }

    @PostMapping("/api/automations")
    AutomationsClient.Job create(@RequestParam String name,
                                 @RequestParam String prompt,
                                 @RequestParam(defaultValue = "daily") String repeat,
                                 @RequestParam(required = false) String time,
                                 @RequestParam(required = false) String date,
                                 @RequestParam(defaultValue = "0") int day,
                                 @RequestParam(defaultValue = "UTC") String timezone,
                                 HttpSession session) {
        String agent = readyAgent(session);
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
    void setEnabled(@PathVariable String id, @RequestParam boolean enabled, HttpSession session) {
        owned(id, session);
        automations.setEnabled(id, enabled);
    }

    @PostMapping("/api/automations/{id}/run")
    void run(@PathVariable String id, HttpSession session) {
        owned(id, session);
        automations.run(id);
    }

    @DeleteMapping("/api/automations/{id}")
    void remove(@PathVariable String id, HttpSession session) {
        owned(id, session);
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

    private String agent(HttpSession session) {
        String user = (String) session.getAttribute(AuthController.USER);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userAgents.ensure(user);
    }

    /** The slug, with the agent and workspace guaranteed to exist before a call. */
    private String readyAgent(HttpSession session) {
        String agent = agent(session);
        try {
            provisioner.ensure(agent);
        } catch (RuntimeException notReady) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "the agent is not ready, try again");
        }
        return agent;
    }

    /**
     * A job is only visible to the account that owns its agent. Authorizing a
     * toggle or a delete must not depend on the provisioner being up, so this
     * uses the stored slug only.
     */
    private void owned(String id, HttpSession session) {
        String agent = agent(session);
        AutomationsClient.Job job = automations.get(id);
        if (job.agentId() == null || !job.agentId().equals(agent)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @ExceptionHandler(AutomationsClient.JobNotFoundException.class)
    ResponseEntity<Void> missing() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** The gateway did not answer; that is an outage, not a missing job. */
    @ExceptionHandler(AutomationsClient.GatewayException.class)
    ResponseEntity<Map<String, String>> gatewayDown(AutomationsClient.GatewayException problem) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "GATEWAY_UNAVAILABLE",
                        "message", "the scheduler is not reachable, try again"));
    }
}
