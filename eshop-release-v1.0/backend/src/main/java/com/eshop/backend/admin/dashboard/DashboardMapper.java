package com.eshop.backend.admin.dashboard;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    @Select("""
            SELECT
                CAST(created_at AS DATE) AS sale_date,
                COUNT(*) AS order_count,
                COALESCE(SUM(total_amount), 0) AS sales_amount
            FROM orders
            WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
              AND created_at >= #{from}
              AND created_at < #{to}
            GROUP BY CAST(created_at AS DATE)
            ORDER BY sale_date ASC
            """)
    List<SalesDayAggregate> selectSalesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT
                product.id AS product_id,
                product.name AS product_name,
                sold.sold_quantity AS sold_quantity,
                sold.sales_amount AS sales_amount
            FROM (
                SELECT
                    item.product_id,
                    SUM(item.quantity) AS sold_quantity,
                    SUM(item.subtotal) AS sales_amount
                FROM order_item item
                INNER JOIN orders shop_order ON shop_order.id = item.order_id
                WHERE shop_order.status IN ('PAID', 'SHIPPED', 'COMPLETED')
                GROUP BY item.product_id
            ) sold
            INNER JOIN product ON product.id = sold.product_id
            WHERE product.status = 'ON_SALE'
            ORDER BY sold.sold_quantity DESC, sold.sales_amount DESC, product.id DESC
            LIMIT #{limit}
            """)
    List<TopProductAggregate> selectTopProducts(@Param("limit") int limit);
}
