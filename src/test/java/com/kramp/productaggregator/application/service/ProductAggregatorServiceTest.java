package com.kramp.productaggregator.application.service;

import com.kramp.productaggregator.application.port.out.AvailabilityPort;
import com.kramp.productaggregator.application.port.out.CatalogPort;
import com.kramp.productaggregator.application.port.out.CustomerPort;
import com.kramp.productaggregator.application.port.out.PricingPort;
import com.kramp.productaggregator.domain.exception.CatalogServiceException;
import com.kramp.productaggregator.domain.exception.UpstreamServiceException;
import com.kramp.productaggregator.domain.model.upstream.AvailabilityData;
import com.kramp.productaggregator.domain.model.upstream.CatalogData;
import com.kramp.productaggregator.domain.model.upstream.CustomerData;
import com.kramp.productaggregator.domain.model.upstream.PricingData;
import com.kramp.productaggregator.infrastructure.config.AggregatorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAggregatorService")
class ProductAggregatorServiceTest {

    @Mock
    CatalogPort catalogPort;
    @Mock
    PricingPort pricingPort;
    @Mock
    AvailabilityPort availabilityPort;
    @Mock
    CustomerPort customerPort;

    ProductAggregatorService service;

    //  Test data
    static final String PRODUCT_ID = "PROD-001";
    static final String MARKET = "pl-PL";
    static final String CUSTOMER_ID = "D123";

    static final CatalogData CATALOG = new CatalogData(
            PRODUCT_ID, "Filtr hydrauliczny", "Opis", "Hydraulics", "OEM",
            Map.of("micronRating", "10µm"),
            List.of("https://cdn.kramp.com/PROD-001/main.jpg")
    );

    static final PricingData PRICING = new PricingData(
            BigDecimal.valueOf(106.96),
            BigDecimal.valueOf(15),
            BigDecimal.valueOf(90.92),
            "PLN"
    );

    static final AvailabilityData AVAILABILITY = new AvailabilityData(
            true, 42, "PL-Poznań", LocalDate.now().plusDays(1)
    );

    static final CustomerData CUSTOMER = new CustomerData(
            CUSTOMER_ID, "DEALER",
            List.of("Hydraulics & Pneumatics"),
            "pl-PL"
    );

    @BeforeEach
    void setUp() {
        var properties = new AggregatorProperties(
                new AggregatorProperties.Timeouts(500, 500, 500, 500)
        );
        service = new ProductAggregatorService(
                catalogPort, pricingPort, availabilityPort, customerPort,
                Executors.newVirtualThreadPerTaskExecutor(),
                properties
        );
    }

    //  Happy path
    @Test
    @DisplayName("All services healthy - fully aggregated response")
    void allServicesHealthy_returnsFullResponse() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(PRODUCT_ID, MARKET, CUSTOMER_ID)).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(PRODUCT_ID, MARKET)).thenReturn(AVAILABILITY);
        when(customerPort.fetchCustomerData(CUSTOMER_ID)).thenReturn(CUSTOMER);

        var result = service.aggregate(PRODUCT_ID, MARKET, CUSTOMER_ID);

        // catalog
        assertThat(result.catalog().name()).isEqualTo("Filtr hydrauliczny");
        assertThat(result.catalog().brand()).isEqualTo("OEM");

        // pricing
        assertThat(result.pricing().available()).isTrue();
        assertThat(result.pricing().finalPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(90.92));
        assertThat(result.pricing().currency()).isEqualTo("PLN");

        // availability
        assertThat(result.availability().stockKnown()).isTrue();
        assertThat(result.availability().inStock()).isTrue();
        assertThat(result.availability().stockLevel()).isEqualTo(42);

        // customer
        assertThat(result.customer().personalized()).isTrue();
        assertThat(result.customer().segment()).isEqualTo("DEALER");

        // metadata
        assertThat(result.metadata().requestId()).isNotBlank();
        assertThat(result.metadata().aggregationDurationMs()).isGreaterThanOrEqualTo(0);
    }

    //  Graceful degradation
    @Test
    @DisplayName("Pricing fails - product returned with pricing.available=false")
    void pricingFails_returnsProductWithPricingUnavailable() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("Pricing down"));
        when(availabilityPort.fetchAvailability(PRODUCT_ID, MARKET)).thenReturn(AVAILABILITY);

        var result = service.aggregate(PRODUCT_ID, MARKET, null);

        assertThat(result.catalog().name()).isEqualTo("Filtr hydrauliczny");
        assertThat(result.pricing().available()).isFalse();
        assertThat(result.pricing().unavailableReason()).isNotBlank();
        assertThat(result.pricing().finalPrice()).isNull();
    }

    @Test
    @DisplayName("Availability fails - product returned with availability.stockKnown=false")
    void availabilityFails_returnsProductWithStockUnknown() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(any(), any(), any())).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(any(), any()))
                .thenThrow(new UpstreamServiceException("Availability down"));

        var result = service.aggregate(PRODUCT_ID, MARKET, null);

        assertThat(result.catalog().name()).isEqualTo("Filtr hydrauliczny");
        assertThat(result.availability().stockKnown()).isFalse();
        assertThat(result.availability().unavailableReason()).isNotBlank();
        assertThat(result.availability().inStock()).isNull();
    }

    @Test
    @DisplayName("Customer fails - standard non-personalized response returned")
    void customerFails_returnsStandardResponse() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(any(), any(), any())).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);
        when(customerPort.fetchCustomerData(CUSTOMER_ID))
                .thenThrow(new UpstreamServiceException("Customer down"));

        var result = service.aggregate(PRODUCT_ID, MARKET, CUSTOMER_ID);

        assertThat(result.customer().personalized()).isFalse();
        assertThat(result.customer().segment()).isNull();
    }

    @Test
    @DisplayName("Pricing and Availability both fail - both sections degraded")
    void bothOptionalServicesFail_bothSectionsDegraded() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(any(), any(), any()))
                .thenThrow(new UpstreamServiceException("Pricing down"));
        when(availabilityPort.fetchAvailability(any(), any()))
                .thenThrow(new UpstreamServiceException("Availability down"));

        var result = service.aggregate(PRODUCT_ID, MARKET, null);

        assertThat(result.catalog().name()).isEqualTo("Filtr hydrauliczny");
        assertThat(result.pricing().available()).isFalse();
        assertThat(result.availability().stockKnown()).isFalse();
    }

    //  Customer Service not called when no customerId
    @Test
    @DisplayName("No customerId - Customer Service never called")
    void noCustomerId_customerServiceNotCalled() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(pricingPort.fetchPricingData(PRODUCT_ID, MARKET, null)).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(PRODUCT_ID, MARKET)).thenReturn(AVAILABILITY);

        var result = service.aggregate(PRODUCT_ID, MARKET, null);

        verifyNoInteractions(customerPort);
        assertThat(result.customer().personalized()).isFalse();
    }

    //  Catalog failure

    @Test
    @DisplayName("Catalog fails - CatalogServiceException thrown - request fails")
    void catalogFails_throwsCatalogServiceException() {
        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET))
                .thenThrow(new UpstreamServiceException("Catalog down"));
        when(pricingPort.fetchPricingData(any(), any(), any())).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);

        assertThatThrownBy(() -> service.aggregate(PRODUCT_ID, MARKET, null))
                .isInstanceOf(CatalogServiceException.class)
                .hasMessageContaining("Catalog Service unavailable");
    }

    // Timeout behavior

    @Test
    @DisplayName("Catalog exceeds timeout - CatalogServiceException thrown")
    void catalogExceedsTimeout_throwsCatalogServiceException() {
        // Aggressive timeout for catalog - 50ms
        var properties = new AggregatorProperties(
                new AggregatorProperties.Timeouts(50, 500, 500, 500)
        );
        service = new ProductAggregatorService(
                catalogPort, pricingPort, availabilityPort, customerPort,
                Executors.newVirtualThreadPerTaskExecutor(),
                properties
        );

        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenAnswer(inv -> {
            Thread.sleep(200);  // 200ms - exceeds 50ms timeout
            return CATALOG;
        });
        when(pricingPort.fetchPricingData(any(), any(), any())).thenReturn(PRICING);
        when(availabilityPort.fetchAvailability(any(), any())).thenReturn(AVAILABILITY);

        assertThatThrownBy(() -> service.aggregate(PRODUCT_ID, MARKET, null))
                .isInstanceOf(CatalogServiceException.class);
    }

    @Test
    @DisplayName("Pricing exceeds timeout - returned as unavailable")
    void pricingExceedsTimeout_returnsUnavailable() {
        // Aggressive timeout - 50ms for pricing, generous for others
        var properties = new AggregatorProperties(
                new AggregatorProperties.Timeouts(500, 50, 500, 500)
        );
        service = new ProductAggregatorService(
                catalogPort, pricingPort, availabilityPort, customerPort,
                Executors.newVirtualThreadPerTaskExecutor(),
                properties
        );

        when(catalogPort.fetchCatalogData(PRODUCT_ID, MARKET)).thenReturn(CATALOG);
        when(availabilityPort.fetchAvailability(PRODUCT_ID, MARKET)).thenReturn(AVAILABILITY);
        when(pricingPort.fetchPricingData(any(), any(), any())).thenAnswer(inv -> {
            Thread.sleep(200);  // 200ms - exceeds 50ms timeout
            return PRICING;
        });

        var result = service.aggregate(PRODUCT_ID, MARKET, null);

        assertThat(result.catalog().name()).isEqualTo("Filtr hydrauliczny");
        assertThat(result.pricing().available()).isFalse();
        assertThat(result.pricing().unavailableReason()).isNotBlank();
    }
}
