package com.openjob;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    static final String USER = "openjob.user";

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService auth;
    private final UserAgentStore agents;
    private final AgentProvisioning provisioning;

    AuthController(AuthService auth, UserAgentStore agents, AgentProvisioning provisioning) {
        this.auth = auth;
        this.agents = agents;
        this.provisioning = provisioning;
    }

    /**
     * The account is written only once its agent exists, so "signed up" always
     * means the app can be used. When the agent cannot be created the handler
     * below answers 503 and nothing is stored.
     */
    @PostMapping("/api/auth/signup")
    ResponseEntity<Void> signup(@RequestParam String email, @RequestParam String password,
                                HttpServletRequest request) {
        String user = auth.verifySignup(email, password, ClientAddress.of(request));
        provisioning.ensure(agents.ensure(user));
        auth.create(user, password);
        signIn(request, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<Void> login(@RequestParam String email, @RequestParam String password,
                               HttpServletRequest request) {
        signIn(request, auth.login(email, password, ClientAddress.of(request)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    ResponseEntity<Map<String, String>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(USER);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("email", user.toString()));
    }

    @ExceptionHandler(AuthService.AuthException.class)
    ResponseEntity<Map<String, String>> failure(AuthService.AuthException problem) {
        return ResponseEntity.status(problem.status)
                .body(Map.of("error", problem.code, "message", problem.getMessage()));
    }

    /** The agent is missing and the sidecar could not create it, so no account was written. */
    @ExceptionHandler(AgentProvisioning.AgentUnavailable.class)
    ResponseEntity<Map<String, String>> agentUnavailable(AgentProvisioning.AgentUnavailable problem) {
        Throwable cause = problem.getCause();
        log.warn("signup_agent_failed slug={} cause={}", problem.slug(),
                cause == null ? "unknown" : cause.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "AGENT_NOT_READY",
                        "message", "Your agent could not be created, so no account was made. "
                                + "Please try again in a moment."));
    }

    private static void signIn(HttpServletRequest request, String user) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute(USER, user);
    }
}
