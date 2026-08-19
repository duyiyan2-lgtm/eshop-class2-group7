package com.eshop.backend.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        Long skuId,
        String productName,
        String skuSpecs,
        String configurationJson,
        String configurationSummary,
        BigDecimal optionAmount,
        String productImage,
        BigDecimal price,
        Integer quantity,
        BigDecimal subtotal
) {
}
