# Product Information Aggregator

Kramp Backend Engineering Assignment.
Backend service aggregating product data from multiple upstream services into a single, market-aware response.

---

## How to Run

**Prerequisites:** Java 21, Maven 3.9+

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.

```bash
# Polish market, anonymous (0% discount)
curl "http://localhost:8080/api/v1/products/PROD-001/aggregate?market=pl-PL"

# Dutch market, dealer customer (15% discount)
curl "http://localhost:8080/api/v1/products/PROD-001/aggregate?market=nl-NL&customerId=D1234"

# German market, premium customer (10% discount)
curl "http://localhost:8080/api/v1/products/PROD-002/aggregate?market=de-DE&customerId=P5678"
```

|                        |                                                                           |
|------------------------|---------------------------------------------------------------------------|
| **Product IDs**        | `PROD-001`, `PROD-002`                                                    |
| **Markets**            | `pl-PL`, `nl-NL`, `de-DE` (others default to English/EUR)                 |
| **Customer ID prefix** | `D*` - 15% discount, `P*` - 10% discount, others - 5%, no customerId - 0% |

### Run tests

```bash
mvn test
```

### Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## Architecture

**Hexagonal Architecture (Ports & Adapters)**

```
infrastructure/adapter/in/rest    ← REST adapter (driving)
application/port/in               ← Primary port (use case interface)
application/service               ← Core aggregation logic
application/port/out              ← Secondary ports (upstream contracts)
infrastructure/adapter/out/mock   ← Mock adapters (driven)
domain/model                      ← Domain records
domain/exception                  ← Domain exceptions
```

Dependency rule: `infrastructure - application - domain`.

Domain has zero Spring dependencies - fully testable without framework context.

---

## Key Design Decisions and Trade-offs

**1. Parallel fan-out with CompletableFuture**

All upstream calls dispatched concurrently via `CompletableFuture.supplyAsync`.

Response time is bounded by the slowest service (~100ms), not the sum (~290ms).

Without parallelism, the page load would be 3× slower - unacceptable on mobile connections in rural areas (explicitly
mentioned in the assignment).

Verified by integration test: `parallel execution - response time bounded by slowest service`.

**2. Required vs Optional distinction**

| Service      | Required | On failure                        |
|--------------|----------|-----------------------------------|
| Catalog      | yes      | HTTP 503                          |
| Pricing      | no       | `pricing.available = false`       |
| Availability | no       | `availability.stockKnown = false` |
| Customer     | no       | `customer.personalized = false`   |

Optional services use `.exceptionally()` to convert any failure into `Optional.empty()` - they can never propagate and
break the response.

Catalog is the only required service - without basic product info, there is nothing meaningful to display.

**3. Status flags instead of nulls**

Each optional section carries a status flag (`available`, `stockKnown`, `personalized`) instead of relying on null
fields.

The frontend knows exactly what to render without guessing the cause of missing data.

Factory methods (`PricingInfo.of(...)`, `PricingInfo.unavailable(...)`) enforce consistency at compile time - it is
impossible to create an "available" price without providing all price fields.

**4. Per-service timeouts (~4× typical latency)**

| Service      | Typical latency | Timeout |
|--------------|-----------------|---------|
| Catalog      | 50ms            | 200ms   |
| Pricing      | 80ms            | 300ms   |
| Availability | 100ms           | 400ms   |
| Customer     | 60ms            | 250ms   |

4× multiplier gives headroom for GC pauses and network variance without triggering false timeouts.

A slow optional service cannot block the entire response beyond its individual timeout.

**5. Java 21 Virtual Threads**

`spring.threads.virtual.enabled=true` enables Virtual Threads for Tomcat.

The upstream executor uses `Executors.newVirtualThreadPerTaskExecutor()`.

Virtual Threads are ideal for I/O-bound calls - the scheduler unmounts a virtual thread during blocking, freeing the
platform thread for other work.

In production these would be real HTTP/gRPC calls - same principle applies.

**6. Realistic mock simulation**

PDF explicitly states: *"The mocks matter - how you simulate upstream services tells us about your understanding of
distributed systems."*

Mocks simulate:

- **Latency variance** - ±20% of base latency + 1% spike at 3× (GC pause, slow DB)
- **Random failures** - statistically correct rates matching declared reliability %

`ThreadLocalRandom` instead of `Random` - each virtual thread gets its own
instance, eliminating CAS contention on a shared seed.

**7. Hexagonal Architecture - extensibility**

Adding a new data source requires only:

1. A new port interface in `application/port/out`
2. A new adapter implementing it
3. Injection into `ProductAggregatorService`

Zero changes to existing code - direct answer to *"without major refactoring"*.

**8. RFC 7807 ProblemDetail**

Structured error responses without a custom DTO - built into Spring 6.

Every error has a consistent `type`, `title`, `status`, `detail`, and `timestamp`.

**9. Circuit Breaker (Resilience4j)**

Each upstream adapter is wrapped with a dedicated `CircuitBreaker` instance configured programmatically.

Resilience4j Spring Boot starter is not yet available for Spring Boot 4.x, so core library is used directly.

Two configuration profiles:

| Profile | Services                        | Opens after     | Wait before retry |
|---------|---------------------------------|-----------------|-------------------|
| strict  | Catalog (required)              | 5/10 calls fail | 10s               |
| relaxed | Pricing, Availability, Customer | 5/10 calls fail | 5s                |

Catalog waits longer (10s) - as a required service, it needs more recovery time.

Optional services use 5s - faster return to full response is preferred.

When the circuit is **OPEN** - upstream service is not called at all.

For optional services this means instant degraded response (0ms wait instead of timeout).

For Catalog this means immediate HTTP 503.

`@Qualifier` ensures each adapter gets its own
`CircuitBreaker` bean - independent state per service, no cross-service interference.
---

## Recommended Future Improvements

- **Caching** - Caffeine cache for catalog data (60s TTL) and pricing (shorter TTL to support flash sales),
  complements Circuit Breaker - cache hits bypass the circuit entirely
- **Observability** - Micrometer + Prometheus + Grafana for metrics,
  `requestId` already in logs - next step is OpenTelemetry trace propagation
- **Rate limiting** - protect the aggregator from client abuse and upstream overload
- **Real upstream clients** - HTTP/gRPC adapters replacing mocks,
  Hexagonal Architecture makes this a drop-in replacement per service
- **Product validation** - unknown productId should return HTTP 404, not mock data
- **gRPC endpoint** - assignment mentions it as a bonus; with Hexagonal
  Architecture it requires only a new adapter in `infrastructure/adapter/in/grpc`

---

## Design Question - Option A

> *"The Assortment team wants to add a 'Related Products' service
> (200ms latency, 90% reliability). Should it be required or optional?"*

**It should be optional.**

A customer viewing a product page has one primary intent: evaluate whether this product meets their need.

Related products are a recommendation enhancement - valuable, but the page is fully functional without them.

Making it required would degrade the experience for 10% of requests (90% reliability = 1 in 10 requests fails) and
extend the critical path from ~100ms to ~200ms.

**How the design accommodates it - three changes, zero impact on existing code:**

1. `RelatedProductsPort` interface in `application/port/out`
2. `MockRelatedProductsAdapter` in `infrastructure/adapter/out/mock`
3. One additional `CompletableFuture` in `ProductAggregatorService`:

```java
var relatedFuture = CompletableFuture
        .supplyAsync(() -> Optional.of(
                relatedProductsPort.fetchRelatedProducts(productId, market)), executor)
        .orTimeout(400, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            log.warn("Related products unavailable: {}", rootCause(ex).getMessage());
            return Optional.empty();
        });
```

**On the 200ms latency:** the critical path grows from ~100ms to ~200ms.

Before implementing, I would discuss with the team whether related products could load client-side asynchronously,
keeping the main aggregation fast and fetching recommendations in a secondary non-blocking request.
