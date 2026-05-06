package com.kramp.productaggregator.infrastructure.adapter.out.mock;

import com.kramp.productaggregator.application.port.out.CatalogPort;
import com.kramp.productaggregator.domain.model.upstream.CatalogData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MockCatalogAdapter extends BaseMockAdapter implements CatalogPort {

    private static final Logger log = LoggerFactory.getLogger(MockCatalogAdapter.class);

    private static final long BASE_LATENCY_MS = 50;
    private static final double RELIABILITY = 0.999;
    private static final String SERVICE_NAME = "CatalogService";

    private final CircuitBreaker circuitBreaker;

    public MockCatalogAdapter(@Qualifier("catalogCB") CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public CatalogData fetchCatalogData(String productId, String market) {
        log.debug("[{}] Fetching productId={}, market={}", SERVICE_NAME, productId, market);

        return circuitBreaker.executeSupplier(() -> {
            simulateLatency(BASE_LATENCY_MS, SERVICE_NAME);
            simulateFailure(RELIABILITY, SERVICE_NAME);
            return buildMockData(productId, market);
        });
    }

    private CatalogData buildMockData(String productId, String market) {
        String name = switch (productId) {
            case "PROD-001" -> switch (market.toLowerCase()) {
                case "pl-pl" -> "Filtr hydrauliczny do ciągnika";
                case "de-de" -> "Hydraulikfilter für Traktor";
                case "nl-nl" -> "Hydraulisch filter voor tractor";
                default -> "Tractor Hydraulic Filter";
            };
            case "PROD-002" -> switch (market.toLowerCase()) {
                case "pl-pl" -> "Łańcuch tnący do piły łańcuchowej";
                case "de-de" -> "Sägekette für Kettensäge";
                case "nl-nl" -> "Zaagketting voor kettingzaag";
                default -> "Chainsaw Replacement Chain";
            };
            default -> "Agricultural Part " + productId;
        };

        String category = switch (productId) {
            case "PROD-001" -> "Hydraulics & Pneumatics";
            case "PROD-002" -> "Forestry & Chainsaw Parts";
            default -> "General Parts";
        };

        Map<String, String> specs = switch (productId) {
            case "PROD-001" -> Map.of(
                    "micronRating", "10µm",
                    "maxPressureBar", "350",
                    "connection", "G3/4 BSP"
            );
            case "PROD-002" -> Map.of(
                    "pitch", "3/8\"",
                    "driveLinks", "72",
                    "lengthCm", "45"
            );
            default -> Map.of("partNumber", productId);
        };

        return new CatalogData(
                productId, name,
                "Genuine spare part for agricultural and machinery applications.",
                category, "OEM", specs,
                List.of(
                        "https://cdn.kramp.com/products/" + productId + "/main.jpg",
                        "https://cdn.kramp.com/products/" + productId + "/detail.jpg"
                )
        );
    }
}
