package com.kramp.productaggregator.application.port.in;

import com.kramp.productaggregator.domain.model.response.ProductAggregateResponse;

public interface ProductAggregatorUseCase {

    /**
     * Aggregates product information from multiple upstream services into a single market-aware response.
     *
     * @param productId  unique product identifier
     * @param market     BCP-47 market code, e.g. "pl-PL", "nl-NL", "de-DE"
     * @param customerId optional customer identifier, null for anonymous requests
     * @return aggregated product response - catalog is guaranteed non-null,
     * all other sections have a status flag describing degraded state
     * @throws com.kramp.productaggregator.domain.exception.CatalogServiceException if the Catalog Service is unavailable
     */

    ProductAggregateResponse aggregate(String productId, String market, String customerId);
}
