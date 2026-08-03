package com.eshop.backend.admin.dashboard;

import java.math.BigDecimal;

public record SalesTrendPointResponse(
        String date,
        long orderCount,
        BigDecimal salesAmount
) {
}
