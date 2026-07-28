package com.eshop.backend.favorite;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FavoriteProductResponse(
        Long id,
        Long categoryId,
        String name,
        String subtitle,
        String mainImage,
        BigDecimal minPrice,
        int totalStock,
        LocalDateTime favoritedAt
) {
}
