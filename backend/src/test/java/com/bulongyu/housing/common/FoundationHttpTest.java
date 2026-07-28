package com.bulongyu.housing.common;

import com.bulongyu.housing.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationHttpTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndPreservesValidRequestId() throws Exception {
        mockMvc.perform(get("/api/health").header(RequestIdFilter.HEADER_NAME, "test-request-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, "test-request-id"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void unknownBusinessEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/private-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME));
    }
}
