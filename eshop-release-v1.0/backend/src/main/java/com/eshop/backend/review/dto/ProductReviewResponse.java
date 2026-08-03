package com.eshop.backend.review.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductReviewResponse(
        Long id,
        Long productId,
        String productName,
        String skuSpecs,
        Integer rating,
        String content,
        String reviewerNickname,
        LocalDateTime createdAt,
        List<String> imageUrls
) {
}
