package com.kramp.productaggregator.domain.exception;

/**
 * Thrown by an adapter when an upstream service is unavailable.
 * <p>
 * Caught by the aggregator service - optional services handle this exception
 * and return a degraded state instead of propagating the failure.
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
