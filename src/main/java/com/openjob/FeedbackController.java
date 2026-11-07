package com.openjob;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint behind the page's Feedback button. Accepts only a message;
 * the browser never sees the Telegram credentials.
 */
@RestController
class FeedbackController {

    record FeedbackRequest(String message) {
    }

    private final FeedbackService feedback;

    FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @PostMapping(path = "/api/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> submit(@RequestBody(required = false) FeedbackRequest body,
                                               HttpServletRequest request) {
        try {
            FeedbackService.Outcome outcome = feedback.submit(
                    body == null ? null : body.message(), ClientAddress.of(request));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accepted", true);
            payload.put("delivered", outcome.delivered());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(payload);
        } catch (FeedbackService.FeedbackException problem) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", problem.code);
            payload.put("message", problem.getMessage());
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(problem.status);
            if (problem.retryAfterSeconds != null) {
                builder.header("Retry-After", String.valueOf(problem.retryAfterSeconds));
            }
            return builder.body(payload);
        }
    }
}
