package com.kramp.productaggregator.infrastructure.adapter.in.rest;

import com.kramp.productaggregator.domain.exception.CatalogServiceException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates domain exceptions into RFC 7807 ProblemDetail responses.
 * <p>
 * Centralized exception handling - controllers stay clean,
 * every error response has a consistent structure.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Catalog failure - HTTP 503.
     * The only upstream failure that terminates the entire request.
     */
    @ExceptionHandler(CatalogServiceException.class)
    public ProblemDetail handleCatalogUnavailable(CatalogServiceException ex) {
        log.error("Catalog Service failure - request cannot be completed: {}", ex.getMessage());

        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Product information is temporarily unavailable. Please try again shortly."
        );
        problem.setTitle("Catalog Service Unavailable");
        problem.setType(URI.create("urn:kramp:error:catalog-unavailable"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Handles @Validated violations on @RequestParam and @PathVariable.
     * e.g. invalid market format, blank productId.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("urn:kramp:error:bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Missing required request parameter - HTTP 400.
     * e.g. market param not provided.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing."
        );
        problem.setTitle("Missing Required Parameter");
        problem.setType(URI.create("urn:kramp:error:bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Catch-all for unexpected errors - HTTP 500.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error during aggregation", ex);

        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("urn:kramp:error:internal"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
