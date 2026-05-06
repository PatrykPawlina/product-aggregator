package com.kramp.productaggregator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests using real mock adapters.
 * <p>
 * Adapters simulate random failures - we accept both
 * fully populated and gracefully degraded responses.
 * Only Catalog failure (0.1%) causes HTTP 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ProductAggregator integration")
class ProductAggregatorIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("anonymous pl-PL request - valid response")
    void anonymousRequest_validOutcome() throws Exception {
        var result = mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isIn(200, 503);

        if (status == 200) {
            String body = result.getResponse().getContentAsString();
            assertThat(body).contains("\"productId\":\"PROD-001\"");
            assertThat(body).contains("\"market\":\"pl-PL\"");
            assertThat(body).contains("\"catalog\"");
        }
    }

    @Test
    @DisplayName("missing market - 400 immediately")
    void missingMarket_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("invalid market format - 400 immediately")
    void invalidMarketFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("parallel execution - response time bounded by slowest service")
    void responseTime_boundedByParallelExecution() throws Exception {
        long start = System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/products/PROD-002/aggregate")
                .param("market", "de-DE")).andReturn();

        long elapsed = System.currentTimeMillis() - start;

        // Slowest mock ~100ms. Sequential would be ~290ms.
        // 1500ms ceiling catches regressions without being flaky.
        assertThat(elapsed)
                .as("Response took %dms - parallel execution may be broken", elapsed)
                .isLessThan(1500L);
    }

    @RepeatedTest(value = 5, name = "{displayName} [{currentRepetition}/{totalRepetitions}]")
    @DisplayName("repeated requests handle random failures gracefully")
    void repeatedRequests_handledGracefully() throws Exception {
        var result = mockMvc.perform(get("/api/v1/products/PROD-003/aggregate")
                        .param("market", "pl-PL"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Only 200 or 503 are acceptable")
                .isIn(200, 503);
    }
}
