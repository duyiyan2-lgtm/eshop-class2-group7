package com.eshop.backend.admin.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DashboardSummaryResponse(
        long userCount,
        long productCount,
        long onSaleProductCount,
        long orderCount,
        long pendingPaymentOrderCount,
        long paidOrderCount,
        long shippedOrderCount,
        long completedOrderCount,
        long canceledOrderCount,
        long lowStockSkuCount,
        BigDecimal paidSalesAmount,
        LocalDateTime generatedAt
) {
}
