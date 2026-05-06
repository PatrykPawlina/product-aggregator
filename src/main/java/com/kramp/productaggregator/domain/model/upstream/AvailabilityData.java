package com.kramp.productaggregator.domain.model.upstream;

import java.time.LocalDate;

public record AvailabilityData(
        boolean inStock,
        int stockLevel,
        String warehouseLocation,
        LocalDate expectedDelivery
) {
}
