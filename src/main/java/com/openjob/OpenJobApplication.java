package com.openjob;

import jakarta.servlet.http.HttpSession;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class OpenJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenJobApplication.class, args);
    }

    @RestController
    static class MessageController {

        private final OpenClawClient openClaw;

        MessageController(OpenClawClient openClaw) {
            this.openClaw = openClaw;
        }

        @PostMapping("/message")
        String message(@RequestParam String message, HttpSession session) {
            return openClaw.reply(message, session.getId());
        }
    }
}
