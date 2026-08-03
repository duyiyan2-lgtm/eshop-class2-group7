package com.eshop.backend.review.dto;

import java.math.BigDecimal;

public record ProductReviewSummaryResponse(
        BigDecimal averageRating,
        long total,
        long fiveStarCount,
        long fourStarCount,
        long threeStarCount,
        long twoStarCount,
        long oneStarCount
) {
}
