package com.kramp.productaggregator.application.service;

import com.kramp.productaggregator.application.port.in.ProductAggregatorUseCase;
import com.kramp.productaggregator.application.port.out.AvailabilityPort;
import com.kramp.productaggregator.application.port.out.CatalogPort;
import com.kramp.productaggregator.application.port.out.CustomerPort;
import com.kramp.productaggregator.application.port.out.PricingPort;
import com.kramp.productaggregator.domain.exception.CatalogServiceException;
import com.kramp.productaggregator.domain.model.response.*;
import com.kramp.productaggregator.domain.model.upstream.AvailabilityData;
import com.kramp.productaggregator.domain.model.upstream.CatalogData;
import com.kramp.productaggregator.domain.model.upstream.CustomerData;
import com.kramp.productaggregator.domain.model.upstream.PricingData;
import com.kramp.productaggregator.infrastructure.config.AggregatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ProductAggregatorService implements ProductAggregatorUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProductAggregatorService.class);

    private final CatalogPort catalogPort;
    private final PricingPort pricingPort;
    private final AvailabilityPort availabilityPort;
    private final CustomerPort customerPort;
    private final ExecutorService executor;
    private final AggregatorProperties properties;

    public ProductAggregatorService(
            CatalogPort catalogPort,
            PricingPort pricingPort,
            AvailabilityPort availabilityPort,
            CustomerPort customerPort,
            @Qualifier("upstreamExecutor")
            ExecutorService executor,
            AggregatorProperties properties) {
        this.catalogPort = catalogPort;
        this.pricingPort = pricingPort;
        this.availabilityPort = availabilityPort;
        this.customerPort = customerPort;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public ProductAggregateResponse aggregate(
            String productId,
            String market,
            String customerId) {

        String requestId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();

        log.info("Aggregation started [requestId={}] productId={}, market={}, hasCustomer={}",
                requestId, productId, market, customerId != null);

        //  Fan-out - all upstream calls dispatched concurrently
        var catalogFuture = CompletableFuture
                .supplyAsync(() -> catalogPort.fetchCatalogData(productId, market), executor)
                .orTimeout(properties.timeouts().catalogMs(), TimeUnit.MILLISECONDS);

        var pricingFuture = CompletableFuture
                .supplyAsync(() -> Optional.of(
                        pricingPort.fetchPricingData(productId, market, customerId)), executor)
                .orTimeout(properties.timeouts().pricingMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[requestId={}] Pricing unavailable: {}",
                            requestId, rootCause(ex).getMessage());
                    return Optional.empty();
                });

        var availabilityFuture = CompletableFuture
                .supplyAsync(() -> Optional.of(
                        availabilityPort.fetchAvailability(productId, market)), executor)
                .orTimeout(properties.timeouts().availabilityMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("[requestId={}] Availability unavailable: {}",
                            requestId, rootCause(ex).getMessage());
                    return Optional.empty();
                });

        // Customer Service is called ONLY when customerId is present
        var customerFuture = (customerId != null && !customerId.isBlank())
                ? CompletableFuture
                  .supplyAsync(() -> Optional.of(
                          customerPort.fetchCustomerData(customerId)), executor)
                  .orTimeout(properties.timeouts().customerMs(), TimeUnit.MILLISECONDS)
                  .exceptionally(ex -> {
                      log.warn("[requestId={}] Customer unavailable: {}",
                              requestId, rootCause(ex).getMessage());
                      return Optional.<CustomerData>empty();
                  })
                : CompletableFuture.completedFuture(Optional.<CustomerData>empty());

        //  Wait for optional services
        //  exceptionally() guarantees these futures never throw
        CompletableFuture.allOf(pricingFuture, availabilityFuture, customerFuture).join();

        //  Catalog is required - failure terminates the request
        CatalogData catalogData;
        try {
            catalogData = catalogFuture.join();
        } catch (CompletionException ex) {
            log.error("[requestId={}] Catalog unavailable for productId={}: {}",
                    requestId, productId, rootCause(ex).getMessage());
            throw new CatalogServiceException(
                    "Catalog Service unavailable - cannot aggregate product: " + productId, ex);
        }

        //  Collect results from optional services
        Optional<PricingData> pricingData = pricingFuture.join();
        Optional<AvailabilityData> availabilityData = availabilityFuture.join();
        Optional<CustomerData> customerData = customerFuture.join();

        long durationMs = System.currentTimeMillis() - startMs;

        log.info("Aggregation completed [requestId={}] durationMs={}, " +
                        "pricing={}, availability={}, personalized={}",
                requestId, durationMs,
                pricingData.isPresent() ? "ok" : "unavailable",
                availabilityData.isPresent() ? "ok" : "unknown",
                customerData.isPresent() ? "yes" : "no");

        return buildResponse(
                productId, market, requestId, durationMs,
                catalogData, pricingData, availabilityData, customerData);
    }

    //  Response assembly
    private ProductAggregateResponse buildResponse(
            String productId,
            String market,
            String requestId,
            long durationMs,
            CatalogData catalog,
            Optional<PricingData> pricing,
            Optional<AvailabilityData> availability,
            Optional<CustomerData> customer) {

        return new ProductAggregateResponse(
                productId,
                market,
                toCatalogInfo(catalog),
                toPricingInfo(pricing),
                toAvailabilityInfo(availability),
                toCustomerInfo(customer),
                new ResponseMetadata(requestId, Instant.now(), durationMs)
        );
    }

    private CatalogInfo toCatalogInfo(CatalogData d) {
        return new CatalogInfo(
                d.name(), d.description(), d.category(), d.brand(), d.specifications(), d.imageUrls()
        );
    }

    private PricingInfo toPricingInfo(Optional<PricingData> pricingData) {
        return pricingData
                .map(d -> PricingInfo.of(
                        d.basePrice(), d.discountPercentage(), d.finalPrice(), d.currency()))
                .orElse(PricingInfo.unavailable("Pricing Service temporarily unavailable"));
    }

    private AvailabilityInfo toAvailabilityInfo(Optional<AvailabilityData> availabilityData) {
        return availabilityData
                .map(d -> AvailabilityInfo.of(
                        d.inStock(), d.stockLevel(), d.warehouseLocation(), d.expectedDelivery()))
                .orElse(AvailabilityInfo.unknown("Availability Service temporarily unavailable"));
    }

    private CustomerInfo toCustomerInfo(Optional<CustomerData> customerData) {
        return customerData
                .map(d -> CustomerInfo.of(d.segment(), d.preferredCategories()))
                .orElse(CustomerInfo.standard());
    }

    //  Helper

    /**
     * Unwraps CompletionException to get the actual cause for meaningful logging.
     * CompletableFuture wraps every exception in CompletionException
     * without unwrapping, logs show "CompletionException" instead of the real error.
     */
    private static Throwable rootCause(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null)
                ? ex.getCause()
                : ex;
    }
}
