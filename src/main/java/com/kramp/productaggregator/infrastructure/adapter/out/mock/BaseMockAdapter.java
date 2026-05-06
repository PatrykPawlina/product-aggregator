package com.kramp.productaggregator.infrastructure.adapter.out.mock;

import com.kramp.productaggregator.domain.exception.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for all mock upstream adapters.
 * <p>
 * Provides realistic simulation of:
 * - Network latency with ±20% variance and occasional 3x spikes
 * - Random failures based on the service reliability percentage from the assignment
 */
abstract class BaseMockAdapter {

    private static final Logger log = LoggerFactory.getLogger(BaseMockAdapter.class);

    /**
     * Simulates realistic network latency.
     * <p>
     * 99% of calls: base latency ±20% variance
     * 1% of calls: base latency ×3 (simulates GC pause, slow DB query)
     *
     * @param baseMs      typical latency declared in the assignment
     * @param serviceName used for logging
     */
    protected void simulateLatency(long baseMs, String serviceName) {
        var rng = ThreadLocalRandom.current();
        long latency;

        if (rng.nextDouble() < 0.01) {
            latency = baseMs * 3;
            log.debug("[{}] Latency spike: {}ms", serviceName, latency);
        } else {
            long variance = (long) (baseMs * 0.2);
            latency = baseMs + rng.nextLong(-variance, variance + 1);
        }

        try {
            Thread.sleep(Math.max(1, latency));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException(
                    serviceName + " interrupted during latency simulation", e);
        }
    }

    /**
     * Simulates random service failure based on declared reliability.
     *
     * @param reliability fraction in [0.0, 1.0] - e.g. 0.995 = 99.5% reliable
     * @param serviceName used for logging
     */
    protected void simulateFailure(double reliability, String serviceName) {
        if (ThreadLocalRandom.current().nextDouble() > reliability) {
            log.warn("[{}] Simulated failure (reliability={})", serviceName, reliability);
            throw new UpstreamServiceException(
                    serviceName + " is currently unavailable (simulated failure)");
        }
    }
}
