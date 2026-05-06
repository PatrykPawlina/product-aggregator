package com.kramp.productaggregator.domain.model.response;

import java.math.BigDecimal;

/**
 * Pricing section of the aggregated response.
 * <p>
 * {@code available=false} - indicates that the Pricing Service was unavailable,
 * and all price fields are null in that case.
 * {@code unavailableReason} - contains the cause.
 * <p>
 * Factory methods enforce consistency between the flag and the price fields.
 */
public record PricingInfo(
        boolean available,
        BigDecimal basePrice,
        BigDecimal discountPercentage,
        BigDecimal finalPrice,
        String currency,
        String unavailableReason
) {
    public static PricingInfo of(
            BigDecimal basePrice,
            BigDecimal discountPercentage,
            BigDecimal finalPrice,
            String currency) {
        return new PricingInfo(true, basePrice, discountPercentage, finalPrice, currency, null);
    }

    public static PricingInfo unavailable(String reason) {
        return new PricingInfo(false, null, null, null, null, reason);
    }
}
