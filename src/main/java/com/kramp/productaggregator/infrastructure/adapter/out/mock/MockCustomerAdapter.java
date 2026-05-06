package com.kramp.productaggregator.infrastructure.adapter.out.mock;

import com.kramp.productaggregator.application.port.out.CustomerPort;
import com.kramp.productaggregator.domain.model.upstream.CustomerData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockCustomerAdapter extends BaseMockAdapter implements CustomerPort {

    private static final Logger log = LoggerFactory.getLogger(MockCustomerAdapter.class);

    private static final long BASE_LATENCY_MS = 60;
    private static final double RELIABILITY = 0.99;
    private static final String SERVICE_NAME = "CustomerService";

    private final CircuitBreaker circuitBreaker;

    public MockCustomerAdapter(@Qualifier("customerCB") CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public CustomerData fetchCustomerData(String customerId) {
        log.debug("[{}] Fetching customerId={}", SERVICE_NAME, customerId);

        return circuitBreaker.executeSupplier(() -> {
            simulateLatency(BASE_LATENCY_MS, SERVICE_NAME);
            simulateFailure(RELIABILITY, SERVICE_NAME);
            return buildMockCustomer(customerId);
        });
    }

    private CustomerData buildMockCustomer(String customerId) {
        return switch (customerId.toUpperCase().charAt(0)) {
            case 'D' -> new CustomerData(customerId, "DEALER",
                    List.of("Hydraulics & Pneumatics", "Engine Parts", "Transmission"),
                    "nl-NL");
            case 'W' -> new CustomerData(customerId, "WORKSHOP",
                    List.of("Filters", "Belts & Chains", "Seals & Gaskets"),
                    "de-DE");
            case 'P' -> new CustomerData(customerId, "PREMIUM",
                    List.of("Precision Agriculture", "Electronics"),
                    "en-GB");
            default -> new CustomerData(customerId, "STANDARD",
                    List.of("Filters", "Crop Protection Equipment"),
                    "pl-PL");
        };
    }
}
