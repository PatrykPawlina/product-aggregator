package com.kramp.productaggregator.domain.model.response;

import java.time.Instant;

public record ResponseMetadata(
        String requestId,
        Instant timestamp,
        long aggregationDurationMs
) {
}
