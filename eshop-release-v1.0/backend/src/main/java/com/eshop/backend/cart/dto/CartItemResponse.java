package com.eshop.backend.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long skuId,
        Long productId,
        String productName,
        String productImage,
        String specsJson,
        BigDecimal price,
        Integer stock,
        Integer quantity,
        Boolean selected,
        Boolean available,
        BigDecimal optionAmount,
        String configurationHash,
        String configurationSummary,
        BigDecimal subtotal
) {
}
