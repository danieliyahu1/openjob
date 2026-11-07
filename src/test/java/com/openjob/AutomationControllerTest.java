package com.openjob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutomationControllerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void dailyBuildsCronInTheUsersTimezone() {
        Map<String, Object> schedule =
                AutomationController.schedule("daily", "09:30", null, 0, "Europe/Berlin");

        assertEquals("cron", schedule.get("kind"));
        assertEquals("30 9 * * *", schedule.get("expr"));
        assertEquals("Europe/Berlin", schedule.get("tz"));
    }

    @Test
    void weeklyUsesTheSelectedDay() {
        Map<String, Object> schedule =
                AutomationController.schedule("weekly", "18:05", null, 1, "UTC");

        assertEquals("5 18 * * 1", schedule.get("expr"));
    }

    @Test
    void onceConvertsLocalTimeToAnInstant() {
        Map<String, Object> schedule =
                AutomationController.schedule("once", null, "2026-11-10T09:00", 0, "Europe/Berlin");

        assertEquals("at", schedule.get("kind"));
        assertEquals("2026-11-10T08:00:00Z", schedule.get("at"));
    }

    @Test
    void describeRendersTheShapesThisAppCreates() throws Exception {
        assertEquals("Every day at 09:30",
                AutomationsClient.describe(json.readTree("{\"kind\":\"cron\",\"expr\":\"30 9 * * *\"}")));
        assertEquals("Every Monday at 18:05",
                AutomationsClient.describe(json.readTree("{\"kind\":\"cron\",\"expr\":\"5 18 * * 1\"}")));
    }
}
