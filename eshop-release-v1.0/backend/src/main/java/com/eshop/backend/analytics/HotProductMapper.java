package com.eshop.backend.analytics;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface HotProductMapper {

    @Select("""
            <script>
            SELECT
                product.id AS product_id,
                product.name,
                product.subtitle,
                product.main_image AS main_image,
                sold.sales_quantity AS sales_quantity,
                sold.sales_amount AS sales_amount,
                (
                  SELECT MIN(sku.price)
                  FROM product_sku sku
                  WHERE sku.product_id = product.id
                    AND sku.status = 'ENABLED'
                ) AS min_price,
                (
                  SELECT COALESCE(SUM(sku.stock), 0)
                  FROM product_sku sku
                  WHERE sku.product_id = product.id
                    AND sku.status = 'ENABLED'
                ) AS total_stock
            FROM (
                SELECT
                    item.product_id,
                    SUM(item.quantity) AS sales_quantity,
                    SUM(item.subtotal) AS sales_amount
                FROM order_item item
                INNER JOIN orders shop_order ON shop_order.id = item.order_id
                WHERE shop_order.status IN ('PAID', 'SHIPPED', 'COMPLETED')
                <if test="cutoff != null">
                  AND shop_order.created_at &gt;= #{cutoff}
                </if>
                GROUP BY item.product_id
            ) sold
            INNER JOIN product ON product.id = sold.product_id
            WHERE product.status = 'ON_SALE'
            ORDER BY sold.sales_quantity DESC, sold.sales_amount DESC, product.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<HotProductResponse> selectHotProducts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
