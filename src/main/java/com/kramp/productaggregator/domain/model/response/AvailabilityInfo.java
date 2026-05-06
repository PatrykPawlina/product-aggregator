package com.kramp.productaggregator.domain.model.response;

import java.time.LocalDate;

/**
 * Availability section of the aggregated response.
 * <p>
 * {@code stockKnown=false} - indicates that the Availability Service was unavailable,
 * and all stock fields are null in that case.
 * {@code unavailableReason} - contains the cause.
 * <p>
 * Factory methods enforce consistency between the flag and the stock fields.
 */
public record AvailabilityInfo(
        boolean stockKnown,
        Boolean inStock,
        Integer stockLevel,
        String warehouseLocation,
        LocalDate expectedDelivery,
        String unavailableReason
) {
    public static AvailabilityInfo of(
            boolean inStock,
            int stockLevel,
            String warehouseLocation,
            LocalDate expectedDelivery) {
        return new AvailabilityInfo(
                true, inStock, stockLevel, warehouseLocation, expectedDelivery, null);
    }

    public static AvailabilityInfo unknown(String reason) {
        return new AvailabilityInfo(
                false, null, null, null, null, reason);
    }
}
