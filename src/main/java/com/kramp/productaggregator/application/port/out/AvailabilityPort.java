package com.kramp.productaggregator.application.port.out;

import com.kramp.productaggregator.domain.model.upstream.AvailabilityData;

public interface AvailabilityPort {

    /**
     * Fetches real-time stock and delivery information for the given product and market.
     *
     * @param productId unique product identifier
     * @param market    BCP-47 market code, e.g. "pl-PL", "nl-NL"
     * @return availability data including stock level and expected delivery date
     * @throws com.kramp.productaggregator.domain.exception.UpstreamServiceException on failure
     */

    AvailabilityData fetchAvailability(String productId, String market);
}
