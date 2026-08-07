package com.eshop.backend.admin.dashboard;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_user
            WHERE role = 'USER'
            <if test="sellerId != null">
              AND id IN (SELECT DISTINCT user_id FROM orders WHERE seller_id = #{sellerId})
            </if>
            </script>
            """)
    long countUsers(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM product
            <if test="sellerId != null">WHERE seller_id = #{sellerId}</if>
            </script>
            """)
    long countProducts(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM product WHERE status = 'ON_SALE'
            <if test="sellerId != null">AND seller_id = #{sellerId}</if>
            </script>
            """)
    long countOnSaleProducts(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM orders
            <if test="sellerId != null">WHERE seller_id = #{sellerId}</if>
            </script>
            """)
    long countOrders(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM orders WHERE status = #{status}
            <if test="sellerId != null">AND seller_id = #{sellerId}</if>
            </script>
            """)
    long countOrdersByStatus(
            @Param("status") String status,
            @Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM product_sku sku
            INNER JOIN product ON product.id = sku.product_id
            WHERE product.status = 'ON_SALE'
              AND sku.status = 'ENABLED'
              AND sku.stock &lt;= 10
            <if test="sellerId != null">
              AND product.seller_id = #{sellerId}
            </if>
            </script>
            """)
    long countLowStockSkus(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT COALESCE(SUM(total_amount), 0)
            FROM orders
            WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
            <if test="sellerId != null">
              AND seller_id = #{sellerId}
            </if>
            </script>
            """)
    BigDecimal sumPaidSalesAmount(@Param("sellerId") Long sellerId);

    @Select("""
            <script>
            SELECT
                CAST(created_at AS DATE) AS sale_date,
                COUNT(*) AS order_count,
                COALESCE(SUM(total_amount), 0) AS sales_amount
            FROM orders
            WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
              AND created_at >= #{from}
              AND created_at &lt; #{to}
            <if test="sellerId != null">
              AND seller_id = #{sellerId}
            </if>
            GROUP BY CAST(created_at AS DATE)
            ORDER BY sale_date ASC
            </script>
            """)
    List<SalesDayAggregate> selectSalesByDay(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("sellerId") Long sellerId);

    @Select("""
            <script>
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
                <if test="sellerId != null">
                  AND shop_order.seller_id = #{sellerId}
                </if>
                GROUP BY item.product_id
            ) sold
            INNER JOIN product ON product.id = sold.product_id
            WHERE product.status = 'ON_SALE'
            <if test="sellerId != null">
              AND product.seller_id = #{sellerId}
            </if>
            ORDER BY sold.sold_quantity DESC, sold.sales_amount DESC, product.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<TopProductAggregate> selectTopProducts(
            @Param("limit") int limit,
            @Param("sellerId") Long sellerId);
}
