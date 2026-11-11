package com.openjob;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The agent API has no token: the slug is the credential, and everything the
 * caller may see is derived from it.
 */
@WebMvcTest(controllers = OpenJobApplication.MessageController.class,
        properties = "OPENJOB_DB_PATH=test.db")
class ApplicationsApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApplicationStore store;
    @MockitoBean
    private UserAgentStore agents;
    @MockitoBean
    private OpenClawClient openClaw;
    @MockitoBean
    private AgentProvisioning provisioning;
    @MockitoBean
    private OperationalMetrics metrics;

    @Test
    void theAgentSlugIsTheOnlyThingNeeded() throws Exception {
        when(agents.visitorFor("u-0123456789ab")).thenReturn("a-abcdefabcdefabcdefabcdef");
        when(store.list("a-abcdefabcdefabcdefabcdef", null)).thenReturn(List.of(
                new ApplicationStore.Job(1, "Acme", "Backend", "https://acme.example/1", "found")));

        mvc.perform(get("/api/applications").header("X-OpenJob-Agent", "u-0123456789ab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Acme"));
    }

    @Test
    void anUnknownAgentIsRejected() throws Exception {
        when(agents.visitorFor("u-ffffffffffff")).thenReturn(null);

        mvc.perform(get("/api/applications").header("X-OpenJob-Agent", "u-ffffffffffff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingAgentHeaderIsABadRequest() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isBadRequest());
    }
}
