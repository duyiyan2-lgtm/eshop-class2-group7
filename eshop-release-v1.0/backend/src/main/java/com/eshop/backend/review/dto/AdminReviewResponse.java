package com.eshop.backend.review.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminReviewResponse(
        Long id,
        Long userId,
        String username,
        String reviewerNickname,
        Long productId,
        String productName,
        String skuSpecs,
        Integer rating,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> imageUrls) {
}
