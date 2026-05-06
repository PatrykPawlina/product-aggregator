package com.kramp.productaggregator.infrastructure.adapter.out.mock;

import com.kramp.productaggregator.application.port.out.PricingPort;
import com.kramp.productaggregator.domain.model.upstream.PricingData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MockPricingAdapter extends BaseMockAdapter implements PricingPort {

    private static final Logger log = LoggerFactory.getLogger(MockPricingAdapter.class);

    private static final long BASE_LATENCY_MS = 80;
    private static final double RELIABILITY = 0.995;
    private static final String SERVICE_NAME = "PricingService";

    private final CircuitBreaker circuitBreaker;

    public MockPricingAdapter(@Qualifier("pricingCB") CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PricingData fetchPricingData(String productId, String market, String customerId) {
        log.debug("[{}] Fetching productId={}, market={}, customerId={}",
                SERVICE_NAME, productId, market, customerId);

        return circuitBreaker.executeSupplier(() -> {
            simulateLatency(BASE_LATENCY_MS, SERVICE_NAME);
            simulateFailure(RELIABILITY, SERVICE_NAME);
            return buildMockPrice(productId, market, customerId);
        });
    }

    private PricingData buildMockPrice(String productId, String market, String customerId) {
        String currency = resolveCurrency(market);

        BigDecimal basePrice = switch (productId) {
            case "PROD-001" -> BigDecimal.valueOf(24.99);
            case "PROD-002" -> BigDecimal.valueOf(18.50);
            default -> BigDecimal.valueOf(49.95);
        };

        if ("PLN".equals(currency)) {
            basePrice = basePrice
                    .multiply(BigDecimal.valueOf(4.28))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discount = resolveDiscount(customerId);
        BigDecimal finalPrice = basePrice
                .multiply(BigDecimal.ONE.subtract(
                        discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        return new PricingData(basePrice, discount, finalPrice, currency);
    }

    private String resolveCurrency(String market) {
        if (market == null) return "EUR";
        return market.toLowerCase().startsWith("pl") ? "PLN" : "EUR";
    }

    private BigDecimal resolveDiscount(String customerId) {
        if (customerId == null || customerId.isBlank()) return BigDecimal.ZERO;
        return switch (customerId.toUpperCase().charAt(0)) {
            case 'D' -> BigDecimal.valueOf(15);
            case 'P' -> BigDecimal.valueOf(10);
            default -> BigDecimal.valueOf(5);
        };
    }
}
