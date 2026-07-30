package com.eshop.backend.admin.dashboard;

import java.math.BigDecimal;

public record TopProductItemResponse(
        Long productId,
        String productName,
        long soldQuantity,
        BigDecimal salesAmount
) {
}
