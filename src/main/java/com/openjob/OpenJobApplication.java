package com.openjob;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
@EnableScheduling
public class OpenJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenJobApplication.class, args);
    }

    @RestController
    static class MessageController {

        private static final Set<String> STATUSES = Set.of("found", "applied", "blocked");

        private final OpenClawClient openClaw;
        private final ApplicationStore store;
        private final OperationalMetrics metrics;
        private final String apiToken;

        MessageController(OpenClawClient openClaw, ApplicationStore store,
                          OperationalMetrics metrics,
                          @Value("${OPENJOB_API_TOKEN:${OPENCLAW_TOKEN:}}") String apiToken) {
            this.openClaw = openClaw;
            this.store = store;
            this.metrics = metrics;
            this.apiToken = apiToken;
        }

        @PostMapping("/message")
        String message(@RequestParam String message, HttpSession session) {
            String context = "[[session:" + session.getId() + "]] ";
            long started = System.nanoTime();
            metrics.agentStarted();
            try {
                String reply = openClaw.reply(context + message, session.getId());
                metrics.agentFinished("success", System.nanoTime() - started);
                return reply;
            } catch (RuntimeException | Error failure) {
                metrics.agentFinished("failure", System.nanoTime() - started);
                throw failure;
            }
        }

        @GetMapping("/api/applications")
        List<ApplicationStore.Job> list(@RequestHeader(value = "X-OpenJob-Token", required = false) String token,
                                        @RequestParam String user,
                                        @RequestParam(required = false) String status) {
            requireToken(token);
            return store.list(user, status);
        }

        @PostMapping("/api/applications")
        long add(@RequestHeader(value = "X-OpenJob-Token", required = false) String token,
                 @RequestParam String user,
                 @RequestParam String company,
                 @RequestParam String title,
                 @RequestParam(required = false) String url) {
            requireToken(token);
            return store.add(user, company, title, url);
        }

        @PatchMapping("/api/applications/{id}")
        void mark(@RequestHeader(value = "X-OpenJob-Token", required = false) String token,
                  @PathVariable long id,
                  @RequestParam String user,
                  @RequestParam String status) {
            requireToken(token);
            if (!STATUSES.contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown status: " + status);
            }
            if (store.mark(user, id, status) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }

        @GetMapping("/api/criteria")
        String criteria(@RequestHeader(value = "X-OpenJob-Token", required = false) String token,
                        @RequestParam String user) {
            requireToken(token);
            String text = store.criteria(user);
            if (text == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no criteria for this user");
            }
            return text;
        }

        @PostMapping("/api/criteria")
        void saveCriteria(@RequestHeader(value = "X-OpenJob-Token", required = false) String token,
                          @RequestParam String user,
                          @RequestParam String text) {
            requireToken(token);
            store.saveCriteria(user, text);
        }

        private void requireToken(String token) {
            if (apiToken.isEmpty() || !apiToken.equals(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
        }
    }
}
