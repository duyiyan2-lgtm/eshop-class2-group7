package com.eshop.backend.analytics;

import java.math.BigDecimal;

public record HotProductResponse(
        Long productId,
        String name,
        String subtitle,
        String mainImage,
        Long salesQuantity,
        BigDecimal salesAmount,
        BigDecimal minPrice,
        Long totalStock) {
}
