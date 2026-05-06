package com.kramp.productaggregator.domain.exception;

/**
 * Thrown when the Catalog Service is unavailable.
 * <p>
 * Unlike other upstream services, missing catalog data makes it impossible to display the product.
 * This exception propagates to the GlobalExceptionHandler and results in HTTP 503.
 */
public class CatalogServiceException extends RuntimeException {

    public CatalogServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
