package com.kramp.productaggregator.infrastructure.adapter.in.rest;

import com.kramp.productaggregator.application.port.in.ProductAggregatorUseCase;
import com.kramp.productaggregator.domain.exception.CatalogServiceException;
import com.kramp.productaggregator.domain.model.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductAggregatorController.class)
@DisplayName("ProductAggregatorController")
class ProductAggregatorControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ProductAggregatorUseCase aggregatorUseCase;

    static ProductAggregateResponse fullResponse() {
        return new ProductAggregateResponse(
                "PROD-001", "pl-PL",
                new CatalogInfo("Filtr hydrauliczny", "Opis",
                        "Hydraulics", "OEM", Map.of(), List.of()),
                PricingInfo.of(BigDecimal.valueOf(106.96),
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(90.92), "PLN"),
                AvailabilityInfo.of(true, 42, "PL-Poznań",
                        LocalDate.now().plusDays(1)),
                CustomerInfo.standard(),
                new ResponseMetadata("req-123", Instant.now(), 98L)
        );
    }

    @Test
    @DisplayName("valid request - 200 OK with aggregated response")
    void validRequest_returns200() throws Exception {
        when(aggregatorUseCase.aggregate("PROD-001", "pl-PL", null))
                .thenReturn(fullResponse());

        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PROD-001"))
                .andExpect(jsonPath("$.market").value("pl-PL"))
                .andExpect(jsonPath("$.catalog.name").value("Filtr hydrauliczny"))
                .andExpect(jsonPath("$.pricing.available").value(true))
                .andExpect(jsonPath("$.pricing.currency").value("PLN"))
                .andExpect(jsonPath("$.availability.stockKnown").value(true))
                .andExpect(jsonPath("$.customer.personalized").value(false))
                .andExpect(jsonPath("$.metadata.requestId").value("req-123"));
    }

    @Test
    @DisplayName("request with customerId - customerId forwarded to use case")
    void withCustomerId_forwardedToUseCase() throws Exception {
        when(aggregatorUseCase.aggregate("PROD-001", "nl-NL", "D123"))
                .thenReturn(fullResponse());

        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "nl-NL")
                        .param("customerId", "D123"))
                .andExpect(status().isOk());

        verify(aggregatorUseCase).aggregate("PROD-001", "nl-NL", "D123");
    }

    @Test
    @DisplayName("missing market - 400 Bad Request")
    void missingMarket_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Required Parameter"));
    }

    @Test
    @DisplayName("invalid market format - 400 Bad Request")
    void invalidMarketFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Catalog Service fails - 503 Service Unavailable")
    void catalogFails_returns503() throws Exception {
        when(aggregatorUseCase.aggregate(any(), any(), any()))
                .thenThrow(new CatalogServiceException(
                        "Catalog down", new RuntimeException()));

        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Catalog Service Unavailable"));
    }

    @Test
    @DisplayName("pricing unavailable - 200 OK with available=false")
    void pricingUnavailable_returns200WithDegradedPricing() throws Exception {
        var degraded = new ProductAggregateResponse(
                "PROD-001", "pl-PL",
                new CatalogInfo("Filtr", "Opis", "Hydraulics",
                        "OEM", Map.of(), List.of()),
                PricingInfo.unavailable("Pricing Service temporarily unavailable"),
                AvailabilityInfo.of(true, 10, "PL-Poznań",
                        LocalDate.now().plusDays(1)),
                CustomerInfo.standard(),
                new ResponseMetadata("req-456", Instant.now(), 210L)
        );
        when(aggregatorUseCase.aggregate("PROD-001", "pl-PL", null))
                .thenReturn(degraded);

        mockMvc.perform(get("/api/v1/products/PROD-001/aggregate")
                        .param("market", "pl-PL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.available").value(false))
                .andExpect(jsonPath("$.pricing.unavailableReason").isNotEmpty())
                .andExpect(jsonPath("$.catalog.name").value("Filtr"));
    }
}
