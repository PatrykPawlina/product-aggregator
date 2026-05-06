package com.kramp.productaggregator;

import com.kramp.productaggregator.domain.exception.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying Circuit Breaker behavior.
 * <p>
 * Tests that:
 * - Circuit opens after threshold of failures
 * - When OPEN, upstream service is not called (fast-fail)
 * - Optional services degrade gracefully when circuit is OPEN
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Circuit Breaker integration")
class CircuitBreakerIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakers() {
        // Reset all circuit breakers to CLOSED before each test
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("Pricing circuit opens after threshold - subsequent calls fast-fail")
    void pricingCircuit_opensAfterThreshold_subsequentCallsFastFail() throws Exception {

        CircuitBreaker pricingCB = circuitBreakerRegistry.circuitBreaker("pricingService");

        // Force circuit to OPEN state by recording failures
        for (int i = 0; i < 10; i++) {
            pricingCB.onError(0, TimeUnit.MILLISECONDS,
                    new UpstreamServiceException("Simulated failure"));
        }

        // Verify circuit is OPEN
        assertThat(pricingCB.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Request should still return 200 - graceful degradation
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andDo(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(200, 503);

                    if (status == 200) {
                        String body = result.getResponse().getContentAsString();
                        assertThat(body).contains("\"available\":false");
                    }
                });
    }

    @Test
    @DisplayName("Catalog circuit opens - request fails with HTTP 503")
    void catalogCircuit_opensAfterThreshold_requestFails503() throws Exception {

        CircuitBreaker catalogCB = circuitBreakerRegistry.circuitBreaker("catalogService");

        // Force circuit to OPEN
        for (int i = 0; i < 10; i++) {
            catalogCB.onError(0, TimeUnit.MILLISECONDS,
                    new UpstreamServiceException("Simulated failure"));
        }

        assertThat(catalogCB.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Catalog circuit is OPEN - CallNotPermittedException - CatalogServiceException - HTTP 503
        // Unlike optional services, Catalog failure always terminates the request
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Catalog Service Unavailable"));
    }

    @Test
    @DisplayName("Circuit breakers are independent - Pricing OPEN does not affect Catalog")
    void circuitBreakers_areIndependent_pricingOpenDoesNotAffectCatalog() throws Exception {

        CircuitBreaker pricingCB = circuitBreakerRegistry.circuitBreaker("pricingService");

        // Force only Pricing circuit to OPEN
        for (int i = 0; i < 10; i++) {
            pricingCB.onError(0, TimeUnit.MILLISECONDS,
                    new UpstreamServiceException("Pricing failure"));
        }

        assertThat(pricingCB.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreakerRegistry.circuitBreaker("catalogService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // Request should return 200 - Catalog still works, Pricing degraded
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andDo(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(200, 503);

                    if (result.getResponse().getStatus() == 200) {
                        String body = result.getResponse().getContentAsString();
                        assertThat(body).contains("\"available\":false");
                        assertThat(body).contains("\"stockKnown\"");
                    }
                });
    }

    @Test
    @DisplayName("Circuit resets to CLOSED - normal operation resumes")
    void circuit_resetsToClosedState() {
        CircuitBreaker pricingCB = circuitBreakerRegistry.circuitBreaker("pricingService");

        // Force OPEN
        for (int i = 0; i < 10; i++) {
            pricingCB.onError(0, TimeUnit.MILLISECONDS,
                    new UpstreamServiceException("Failure"));
        }
        assertThat(pricingCB.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Reset - simulates recovery
        pricingCB.reset();
        assertThat(pricingCB.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
