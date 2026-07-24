package com.eshop.backend.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eshop.backend.catalog.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    @Select("""
            SELECT COUNT(*)
            FROM order_item oi
            INNER JOIN orders o ON o.id = oi.order_id
            WHERE oi.product_id = #{productId}
              AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED')
            """)
    long countActiveOrders(@Param("productId") Long productId);
}
