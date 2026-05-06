package com.kramp.productaggregator.domain.model.response;

import java.util.List;

/**
 * Customer context section of the aggregated response.
 * <p>
 * {@code personalized=false} - indicates that either no customerId was provided
 * or the Customer Service was unavailable - segment and preferences are null in that case.
 * <p>
 * Factory methods enforce consistency between the flag and the customer fields.
 */
public record CustomerInfo(
        boolean personalized,
        String segment,
        List<String> preferredCategories
) {
    public static CustomerInfo of(String segment, List<String> preferredCategories) {
        return new CustomerInfo(true, segment, preferredCategories);
    }

    public static CustomerInfo standard() {
        return new CustomerInfo(false, null, null);
    }
}
