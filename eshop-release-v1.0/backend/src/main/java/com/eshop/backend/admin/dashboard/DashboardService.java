package com.eshop.backend.admin.dashboard;

import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardMapper dashboardMapper;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(LoginUser operator) {
        Long sellerId = sellerId(operator);
        BigDecimal paidSalesAmount = dashboardMapper.sumPaidSalesAmount(sellerId);
        return new DashboardSummaryResponse(
                dashboardMapper.countUsers(sellerId),
                dashboardMapper.countProducts(sellerId),
                dashboardMapper.countOnSaleProducts(sellerId),
                dashboardMapper.countOrders(sellerId),
                dashboardMapper.countOrdersByStatus("PENDING_PAYMENT", sellerId),
                dashboardMapper.countOrdersByStatus("PAID", sellerId),
                dashboardMapper.countOrdersByStatus("SHIPPED", sellerId),
                dashboardMapper.countOrdersByStatus("COMPLETED", sellerId),
                dashboardMapper.countOrdersByStatus("CANCELED", sellerId),
                dashboardMapper.countLowStockSkus(sellerId),
                paidSalesAmount == null ? BigDecimal.ZERO : paidSalesAmount,
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public SalesTrendResponse salesTrend(LoginUser operator, Integer days) {
        int range = days == null ? 7 : days;
        if (range < 1 || range > 30) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(range - 1L);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        List<SalesDayAggregate> aggregates = dashboardMapper.selectSalesByDay(from, to, sellerId(operator));
        Map<LocalDate, SalesDayAggregate> byDate = new HashMap<>();
        if (aggregates != null) {
            for (SalesDayAggregate aggregate : aggregates) {
                if (aggregate.getSaleDate() != null) {
                    byDate.put(aggregate.getSaleDate(), aggregate);
                }
            }
        }

        List<SalesTrendPointResponse> points = new ArrayList<>(range);
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            SalesDayAggregate aggregate = byDate.get(date);
            long orderCount = aggregate == null ? 0L : aggregate.getOrderCount();
            BigDecimal amount = aggregate == null || aggregate.getSalesAmount() == null
                    ? BigDecimal.ZERO
                    : aggregate.getSalesAmount();
            points.add(new SalesTrendPointResponse(date.toString(), orderCount, amount));
        }
        return new SalesTrendResponse(range, points);
    }

    @Transactional(readOnly = true)
    public TopProductsResponse topProducts(LoginUser operator, Integer limit) {
        int size = limit == null ? 10 : limit;
        if (size < 1 || size > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        List<TopProductAggregate> rows = dashboardMapper.selectTopProducts(size, sellerId(operator));
        List<TopProductItemResponse> items = rows == null
                ? List.of()
                : rows.stream()
                .map(row -> new TopProductItemResponse(
                        row.getProductId(),
                        row.getProductName(),
                        row.getSoldQuantity(),
                        row.getSalesAmount() == null ? BigDecimal.ZERO : row.getSalesAmount()))
                .toList();
        return new TopProductsResponse(size, items);
    }

    private Long sellerId(LoginUser operator) {
        return operator != null && "SELLER".equals(operator.getRole())
                ? operator.getUserId()
                : null;
    }
}
