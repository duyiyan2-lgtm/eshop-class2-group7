package com.eshop.backend.admin.dashboard;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();

    @Select("SELECT COUNT(*) FROM product")
    long countProducts();

    @Select("SELECT COUNT(*) FROM product WHERE status = 'ON_SALE'")
    long countOnSaleProducts();

    @Select("SELECT COUNT(*) FROM orders")
    long countOrders();

    @Select("SELECT COUNT(*) FROM orders WHERE status = #{status}")
    long countOrdersByStatus(@Param("status") String status);

    @Select("""
            SELECT COUNT(*)
            FROM product_sku sku
            INNER JOIN product ON product.id = sku.product_id
            WHERE product.status = 'ON_SALE'
              AND sku.status = 'ENABLED'
              AND sku.stock <= 10
            """)
    long countLowStockSkus();

    @Select("""
            SELECT COALESCE(SUM(total_amount), 0)
            FROM orders
            WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
            """)
    BigDecimal sumPaidSalesAmount();
}
