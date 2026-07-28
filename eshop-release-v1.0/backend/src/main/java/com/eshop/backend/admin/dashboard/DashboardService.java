package com.eshop.backend.admin.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardMapper dashboardMapper;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        BigDecimal paidSalesAmount = dashboardMapper.sumPaidSalesAmount();
        return new DashboardSummaryResponse(
                dashboardMapper.countUsers(),
                dashboardMapper.countProducts(),
                dashboardMapper.countOnSaleProducts(),
                dashboardMapper.countOrders(),
                dashboardMapper.countOrdersByStatus("PENDING_PAYMENT"),
                dashboardMapper.countOrdersByStatus("PAID"),
                dashboardMapper.countOrdersByStatus("SHIPPED"),
                dashboardMapper.countOrdersByStatus("COMPLETED"),
                dashboardMapper.countOrdersByStatus("CANCELED"),
                dashboardMapper.countLowStockSkus(),
                paidSalesAmount == null ? BigDecimal.ZERO : paidSalesAmount,
                LocalDateTime.now());
    }
}
