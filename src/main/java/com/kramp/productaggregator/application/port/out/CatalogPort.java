package com.kramp.productaggregator.application.port.out;

import com.kramp.productaggregator.domain.model.upstream.CatalogData;

public interface CatalogPort {

    /**
     * Fetches product catalog data for the given product and market.
     *
     * @param productId unique product identifier
     * @param market    BCP-47 market code, e.g. "pl-PL", "nl-NL"
     * @return catalog data for the requested product and market
     * @throws com.kramp.productaggregator.domain.exception.UpstreamServiceException on failure
     */

    CatalogData fetchCatalogData(String productId, String market);
}
