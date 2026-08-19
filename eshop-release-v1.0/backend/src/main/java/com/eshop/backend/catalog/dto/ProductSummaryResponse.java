package com.eshop.backend.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long id,
        Long categoryId,
        String name,
        String subtitle,
        String mainImage,
        String status,
        String productKind,
        BigDecimal minPrice,
        int totalStock,
        LocalDateTime createdAt
) {
}
