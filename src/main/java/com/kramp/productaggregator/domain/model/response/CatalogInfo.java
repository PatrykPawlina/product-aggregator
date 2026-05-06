package com.kramp.productaggregator.domain.model.response;

import java.util.List;
import java.util.Map;

public record CatalogInfo(
        String name,
        String description,
        String category,
        String brand,
        Map<String, String> specifications,
        List<String> imageUrls
) {
}
