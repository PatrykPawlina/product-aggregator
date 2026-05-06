package com.kramp.productaggregator.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the aggregator.
 * <p>
 * Bound from application.yml under the "aggregator" prefix.
 * Using @ConfigurationProperties over @Value because:
 * - groups related properties in one place
 * - provides type safety and IDE autocomplete
 * - works seamlessly with Java records
 */
@ConfigurationProperties(prefix = "aggregator")
public record AggregatorProperties(Timeouts timeouts) {

    public record Timeouts(
            long catalogMs,
            long pricingMs,
            long availabilityMs,
            long customerMs
    ) {
    }
}
