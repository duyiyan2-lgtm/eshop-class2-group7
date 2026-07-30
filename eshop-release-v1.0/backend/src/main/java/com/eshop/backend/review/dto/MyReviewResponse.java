package com.eshop.backend.review.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyReviewResponse(
        Long id,
        Long productId,
        Long orderId,
        Long orderItemId,
        String productName,
        String skuSpecs,
        String productImage,
        Integer rating,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> imageUrls
) {
}
