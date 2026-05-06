package com.kramp.productaggregator.infrastructure.adapter.in.rest;

import com.kramp.productaggregator.application.port.in.ProductAggregatorUseCase;
import com.kramp.productaggregator.domain.model.response.ProductAggregateResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST adapter for the Product Information Aggregator.
 * <p>
 * Thin layer - responsible only for HTTP concerns:
 * input validation, delegating to the use case, and mapping to HTTP response.
 * No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductAggregatorController {

    private final ProductAggregatorUseCase aggregatorUseCase;

    public ProductAggregatorController(ProductAggregatorUseCase aggregatorUseCase) {
        this.aggregatorUseCase = aggregatorUseCase;
    }

    /**
     * Aggregates product information for a given customer context.
     *
     * @param productId  unique product identifier
     * @param market     BCP-47 market code, e.g. "pl-PL", "nl-NL", "de-DE"
     * @param customerId optional customer identifier - omit for anonymous requests
     * @return aggregated product response
     * <p>
     * Example requests:
     * GET /api/v1/products/PROD-001/aggregate?market=pl-PL
     * GET /api/v1/products/PROD-001/aggregate?market=nl-NL&customerId=D1234
     */
    @GetMapping("/{productId}/aggregate")
    public ResponseEntity<ProductAggregateResponse> aggregate(
            @PathVariable
            @NotBlank(message = "productId must not be blank")
            String productId,

            @RequestParam
            @NotBlank(message = "market is required")
            @Pattern(
                    regexp = "^[a-zA-Z]{2}-[a-zA-Z]{2}$",
                    message = "market must be a valid BCP-47 code, e.g. pl-PL, nl-NL, de-DE"
            )
            String market,

            @RequestParam(required = false)
            String customerId) {

        var response = aggregatorUseCase.aggregate(productId, market, customerId);
        return ResponseEntity.ok(response);
    }
}
