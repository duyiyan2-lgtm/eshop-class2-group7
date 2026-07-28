package com.eshop.backend.admin.inventory;

import java.math.BigDecimal;

public record InventoryAlertResponse(
        Long skuId,
        Long productId,
        String productName,
        String mainImage,
        String skuCode,
        String specsJson,
        BigDecimal price,
        Integer stock,
        String skuStatus,
        String productStatus
) {
}
