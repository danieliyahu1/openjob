package com.openjob;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

        /**
         * A demo turn can run for minutes against real browser automation, so
         * both the visitor and the network have a ceiling. Real testers send a
         * handful of messages and never see either.
         */
        private static final int MESSAGES_PER_VISITOR_HOUR = 30;
        private static final int MESSAGES_PER_NETWORK_HOUR = 100;

        private final RateLimiter messages = new RateLimiter(MESSAGES_PER_VISITOR_HOUR, Duration.ofHours(1));
        private final RateLimiter network =
                new RateLimiter(MESSAGES_PER_NETWORK_HOUR, Duration.ofHours(1));

        private final OpenClawClient openClaw;
        private final ApplicationStore store;
        private final UserAgentStore userAgents;
        private final AgentProvisioning provisioning;
        private final OperationalMetrics metrics;

        MessageController(OpenClawClient openClaw, ApplicationStore store,
                          UserAgentStore userAgents, AgentProvisioning provisioning,
                          OperationalMetrics metrics) {
            this.openClaw = openClaw;
            this.store = store;
            this.userAgents = userAgents;
            this.provisioning = provisioning;
            this.metrics = metrics;
        }

        /**
         * Forwards one turn to the visitor's agent. Each failure says what
         * actually happened: a timeout is not a failure, and an unreachable
         * gateway means the turn never started.
         */
        @PostMapping("/message")
        ResponseEntity<String> message(@RequestParam String message, HttpServletRequest request) {
            String user = VisitorIdentity.require(request);
            long wait = messages.permit(user);
            if (wait == 0) {
                wait = network.permit(ClientAddress.of(request));
            }
            if (wait > 0) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", String.valueOf(wait))
                        .body("The demo allows " + MESSAGES_PER_VISITOR_HOUR
                                + " messages an hour. Try again in " + wait + " seconds.");
            }
            String agent = userAgents.ensure(user);
            try {
                provisioning.ensure(agent);
            } catch (AgentProvisioning.AgentUnavailable notReady) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Your agent is not ready yet, so this message was not sent. "
                                + "Please try again in a moment.");
            }

            long started = System.nanoTime();
            metrics.agentStarted();
            try {
                String reply = openClaw.reply("openclaw/" + agent, message, user);
                metrics.agentFinished("success", System.nanoTime() - started);
                return ResponseEntity.ok(reply);
            } catch (OpenClawClient.Timeout noAnswer) {
                metrics.agentFinished("timeout", System.nanoTime() - started);
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body("The agent did not answer in time. It may still be working, "
                                + "so check the board before sending the message again.");
            } catch (OpenClawClient.Unreachable neverStarted) {
                metrics.agentFinished("failure", System.nanoTime() - started);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("The agent could not be reached, so this message was not sent. "
                                + "Please try again in a moment.");
            } catch (OpenClawClient.Rejected refused) {
                metrics.agentFinished("failure", System.nanoTime() - started);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("The agent refused the request (HTTP " + refused.status()
                                + ").");
            } catch (RuntimeException | Error failure) {
                metrics.agentFinished("failure", System.nanoTime() - started);
                throw failure;
            }
        }

        /**
         * The agent id is the credential: it is the random slug the provisioner
         * writes into the workspace, so it is only known to that account's agent.
         * There is deliberately no shared token; see the README.
         */
        @GetMapping("/api/applications")
        List<ApplicationStore.Job> list(@RequestHeader("X-OpenJob-Agent") String agent,
                                        @RequestParam(required = false) String status) {
            return store.list(owner(agent), status);
        }

        @PostMapping("/api/applications")
        long add(@RequestHeader("X-OpenJob-Agent") String agent,
                 @RequestParam String company,
                 @RequestParam String title,
                 @RequestParam(required = false) String url) {
            return store.add(owner(agent), company, title, url);
        }

        @PatchMapping("/api/applications/{id}")
        void mark(@RequestHeader("X-OpenJob-Agent") String agent,
                  @PathVariable long id,
                  @RequestParam String status) {
            if (!STATUSES.contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown status: " + status);
            }
            if (store.mark(owner(agent), id, status) == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }

        @GetMapping("/api/criteria")
        String criteria(@RequestHeader("X-OpenJob-Agent") String agent) {
            String text = store.criteria(owner(agent));
            if (text == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no criteria for this user");
            }
            return text;
        }

        @PostMapping("/api/criteria")
        void saveCriteria(@RequestHeader("X-OpenJob-Agent") String agent,
                          @RequestParam String text) {
            store.saveCriteria(owner(agent), text);
        }

        private String owner(String agent) {
            String visitor = userAgents.visitorFor(agent);
            if (visitor == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown agent");
            }
            return visitor;
        }
    }
}
