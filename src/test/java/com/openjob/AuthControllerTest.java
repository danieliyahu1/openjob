package com.openjob;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An account exists only when its agent does, so a provisioning failure must
 * leave nothing behind and must say so.
 */
@WebMvcTest(controllers = AuthController.class, properties = "OPENJOB_DB_PATH=test.db")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;
    @MockitoBean
    private UserAgentStore agents;
    @MockitoBean
    private AgentProvisioning provisioning;
    @MockitoBean
    private ApplicationStore store;
    @MockitoBean
    private OpenClawClient openClaw;
    @MockitoBean
    private OperationalMetrics metrics;

    @Test
    void aFailedAgentCreationLeavesNoAccount() throws Exception {
        when(auth.verifySignup(anyString(), anyString(), anyString())).thenReturn("me@example.com");
        when(agents.ensure("me@example.com")).thenReturn("u-0123456789ab");
        doThrow(new AgentProvisioning.AgentUnavailable("u-0123456789ab", new IllegalStateException("down")))
                .when(provisioning).ensure("u-0123456789ab");

        mvc.perform(post("/api/auth/signup")
                        .param("email", "me@example.com")
                        .param("password", "correct horse"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AGENT_NOT_READY"))
                .andExpect(jsonPath("$.message", containsString("no account was made")));

        verify(auth, never()).create(anyString(), anyString());
    }

    @Test
    void aReadyAgentCreatesTheAccountAndSignsIn() throws Exception {
        when(auth.verifySignup(anyString(), anyString(), anyString())).thenReturn("me@example.com");
        when(agents.ensure("me@example.com")).thenReturn("u-0123456789ab");

        mvc.perform(post("/api/auth/signup")
                        .param("email", "me@example.com")
                        .param("password", "correct horse"))
                .andExpect(status().isNoContent())
                .andExpect(request().sessionAttribute(AuthController.USER, "me@example.com"));

        verify(provisioning).ensure("u-0123456789ab");
        verify(auth).create("me@example.com", "correct horse");
    }
}
