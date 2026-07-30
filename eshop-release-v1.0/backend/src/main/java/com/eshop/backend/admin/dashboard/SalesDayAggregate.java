package com.eshop.backend.admin.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesDayAggregate {
    private LocalDate saleDate;
    private long orderCount;
    private BigDecimal salesAmount;

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getSalesAmount() {
        return salesAmount;
    }

    public void setSalesAmount(BigDecimal salesAmount) {
        this.salesAmount = salesAmount;
    }
}
