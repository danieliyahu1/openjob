package com.openjob;

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

        @PostMapping("/message")
        String message(@RequestParam(defaultValue = "nothing") String message) {
            return "your message is: " + message;
        }
    }
}
