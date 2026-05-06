package com.kramp.productaggregator.infrastructure.adapter.out.mock;

import com.kramp.productaggregator.application.port.out.AvailabilityPort;
import com.kramp.productaggregator.domain.model.upstream.AvailabilityData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockAvailabilityAdapter extends BaseMockAdapter implements AvailabilityPort {

    private static final Logger log = LoggerFactory.getLogger(MockAvailabilityAdapter.class);

    private static final long BASE_LATENCY_MS = 100;
    private static final double RELIABILITY = 0.98;
    private static final String SERVICE_NAME = "AvailabilityService";

    private final CircuitBreaker circuitBreaker;

    public MockAvailabilityAdapter(@Qualifier("availabilityCB") CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public AvailabilityData fetchAvailability(String productId, String market) {
        log.debug("[{}] Fetching productId={}, market={}", SERVICE_NAME, productId, market);

        return circuitBreaker.executeSupplier(() -> {
            simulateLatency(BASE_LATENCY_MS, SERVICE_NAME);
            simulateFailure(RELIABILITY, SERVICE_NAME);
            return buildMockAvailability(productId, market);
        });
    }

    private AvailabilityData buildMockAvailability(String productId, String market) {
        var rng = ThreadLocalRandom.current();

        int stockLevel = switch (productId) {
            case "PROD-001" -> rng.nextInt(0, 150);
            case "PROD-002" -> rng.nextInt(0, 80);
            default -> rng.nextInt(0, 50);
        };

        boolean inStock = stockLevel > 0;

        LocalDate expectedDelivery = inStock
                ? LocalDate.now().plusDays(1)
                : LocalDate.now().plusDays(rng.nextInt(5, 8));

        return new AvailabilityData(
                inStock, stockLevel,
                resolveWarehouse(market),
                expectedDelivery
        );
    }

    private String resolveWarehouse(String market) {
        if (market == null) return "NL-Varsseveld";
        return switch (market.toLowerCase()) {
            case "pl-pl" -> "PL-Poznań";
            case "de-de" -> "DE-Neumünster";
            case "nl-nl" -> "NL-Varsseveld";
            case "fr-fr" -> "FR-Châteaubriant";
            case "en-gb" -> "GB-Peterborough";
            default -> "NL-Varsseveld";
        };
    }
}
