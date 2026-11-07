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
    private final ProvisionerClient provisioner;

    AuthController(AuthService auth, UserAgentStore agents, ProvisionerClient provisioner) {
        this.auth = auth;
        this.agents = agents;
        this.provisioner = provisioner;
    }

    @PostMapping("/api/auth/signup")
    ResponseEntity<Void> signup(@RequestParam String email, @RequestParam String password,
                                HttpServletRequest request) {
        String user = auth.signup(email, password, client(request));
        signIn(request, user);
        prepare(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<Void> login(@RequestParam String email, @RequestParam String password,
                               HttpServletRequest request) {
        signIn(request, auth.login(email, password, client(request)));
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

    private static void signIn(HttpServletRequest request, String user) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute(USER, user);
    }

    /** Best effort: /message provisions again, so a failure here only costs time. */
    private void prepare(String user) {
        try {
            provisioner.ensure(agents.ensure(user));
        } catch (RuntimeException problem) {
            log.warn("agent_provisioning_failed slug={} error={}",
                    agents.slugFor(user), problem.getClass().getSimpleName());
        }
    }

    /** Cloudflare puts the visitor address first in X-Forwarded-For. */
    private static String client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
