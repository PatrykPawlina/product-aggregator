package com.kramp.productaggregator.application.port.out;

import com.kramp.productaggregator.domain.model.upstream.CustomerData;

public interface CustomerPort {

    /**
     * Fetches customer segment and preference data.
     * Called only when a customerId is present in the request.
     *
     * @param customerId unique customer identifier
     * @return customer context data including segment and preferences
     * @throws com.kramp.productaggregator.domain.exception.UpstreamServiceException on failure
     */

    CustomerData fetchCustomerData(String customerId);
}
