package com.kramp.productaggregator.application.port.out;

import com.kramp.productaggregator.domain.model.upstream.PricingData;

public interface PricingPort {

    /**
     * Fetches pricing data for the given product, market and optional customer.
     *
     * @param productId  unique product identifier
     * @param market     BCP-47 market code, e.g. "pl-PL", "nl-NL"
     * @param customerId optional customer identifier, null for anonymous requests
     * @return pricing data including any customer-specific discount
     * @throws com.kramp.productaggregator.domain.exception.UpstreamServiceException on failure
     */

    PricingData fetchPricingData(String productId, String market, String customerId);
}
