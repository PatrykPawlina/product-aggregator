package com.kramp.productaggregator.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic Circuit Breaker configuration.
 * <p>
 * Using core Resilience4j without Spring Boot starter - compatible with Spring Boot 4.x.
 * <p>
 * Two config profiles:
 * - strict  - Catalog (required service - faster reaction to failures)
 * - relaxed - Pricing, Availability, Customer (optional services)
 */
@Configuration
public class CircuitBreakerConfiguration {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {

        // Catalog - required service, aggressive settings
        // Opens faster to fail-fast when catalog is degraded
        var strictConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        // Optional services - more lenient settings
        // Gives degraded services more time to recover
        var relaxedConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        var registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("catalogService", strictConfig);
        registry.circuitBreaker("pricingService", relaxedConfig);
        registry.circuitBreaker("availabilityService", relaxedConfig);
        registry.circuitBreaker("customerService", relaxedConfig);

        return registry;
    }

    @Bean
    public CircuitBreaker catalogCB(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("catalogService");
    }

    @Bean
    public CircuitBreaker pricingCB(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("pricingService");
    }

    @Bean
    public CircuitBreaker availabilityCB(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("availabilityService");
    }

    @Bean
    public CircuitBreaker customerCB(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("customerService");
    }
}
