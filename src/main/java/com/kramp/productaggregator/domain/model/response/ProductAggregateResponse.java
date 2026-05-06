package com.kramp.productaggregator.domain.model.response;

/**
 * Aggregated product response returned to the client.
 * <p>
 * {@code catalog} is guaranteed to be non-null.
 * The request fails with HTTP 503 if the Catalog Service is unavailable.
 * <p>
 * All other sections have a status flag describing their degraded state when the upstream service is unavailable.
 */
public record ProductAggregateResponse(
        String productId,
        String market,
        CatalogInfo catalog,
        PricingInfo pricing,
        AvailabilityInfo availability,
        CustomerInfo customer,
        ResponseMetadata metadata
) {
}
