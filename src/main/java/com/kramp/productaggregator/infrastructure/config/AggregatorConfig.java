package com.kramp.productaggregator.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core infrastructure configuration.
 */
@Configuration
public class AggregatorConfig {

    /**
     * Virtual thread executor for upstream service calls.
     * <p>
     * Each upstream call blocks on Thread.sleep() - simulating I/O latency.
     * Virtual Threads are ideal here: the scheduler unmounts a virtual thread
     * during blocking, freeing the platform thread for other work.
     * In production these would be real HTTP/gRPC calls - same principle applies.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService upstreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
