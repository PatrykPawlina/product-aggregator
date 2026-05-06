package com.kramp.productaggregator.domain.model.upstream;

import java.math.BigDecimal;

public record PricingData(
        BigDecimal basePrice,
        BigDecimal discountPercentage,
        BigDecimal finalPrice,
        String currency
) {
}
