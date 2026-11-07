package com.openjob;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Each chat failure tells the user what really happened. */
@WebMvcTest(controllers = OpenJobApplication.MessageController.class,
        properties = "OPENJOB_DB_PATH=test.db")
class MessageApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OpenClawClient openClaw;
    @MockitoBean
    private ApplicationStore store;
    @MockitoBean
    private UserAgentStore agents;
    @MockitoBean
    private AgentProvisioning provisioning;
    @MockitoBean
    private OperationalMetrics metrics;

    @Test
    void anAgentThatIsNotReadySaysTheMessageWasNotSent() throws Exception {
        when(agents.ensure("me@example.com")).thenReturn("u-0123456789ab");
        doThrow(new AgentProvisioning.AgentUnavailable("u-0123456789ab", new IllegalStateException("down")))
                .when(provisioning).ensure("u-0123456789ab");

        mvc.perform(message())
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("was not sent")));
    }

    @Test
    void aTurnThatTimedOutIsNotReportedAsAFailure() throws Exception {
        when(agents.ensure("me@example.com")).thenReturn("u-0123456789ab");
        when(openClaw.reply(anyString(), anyString(), anyString()))
                .thenThrow(new OpenClawClient.Timeout(null));

        mvc.perform(message())
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().string(containsString("may still be working")));
    }

    @Test
    void aRefusedTurnReportsTheGatewayStatus() throws Exception {
        when(agents.ensure("me@example.com")).thenReturn("u-0123456789ab");
        when(openClaw.reply(anyString(), anyString(), anyString()))
                .thenThrow(new OpenClawClient.Rejected(400, null));

        mvc.perform(message())
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("HTTP 400")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder message() {
        return post("/message")
                .param("message", "any news?")
                .sessionAttr(AuthController.USER, "me@example.com");
    }
}
