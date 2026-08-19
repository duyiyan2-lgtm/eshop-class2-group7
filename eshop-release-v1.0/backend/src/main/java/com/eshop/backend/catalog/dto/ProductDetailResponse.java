package com.eshop.backend.catalog.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String subtitle,
        String mainImage,
        String detail,
        String status,
        String productKind,
        List<SkuResponse> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
