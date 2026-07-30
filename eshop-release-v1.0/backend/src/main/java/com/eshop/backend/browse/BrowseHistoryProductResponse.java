package com.eshop.backend.browse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BrowseHistoryProductResponse(
        Long productId,
        String name,
        String subtitle,
        String mainImage,
        BigDecimal minPrice,
        Long totalStock,
        LocalDateTime browsedAt
) {
}
