package com.eshop.backend.catalog.dto;

import java.math.BigDecimal;

public record SkuResponse(
        Long id,
        Long productId,
        String skuCode,
        String specsJson,
        BigDecimal price,
        Integer stock,
        String status
) {
}
