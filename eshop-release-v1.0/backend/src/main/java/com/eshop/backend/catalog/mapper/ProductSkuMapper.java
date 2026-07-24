package com.eshop.backend.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eshop.backend.catalog.entity.ProductSku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Update("""
            UPDATE product_sku
            SET stock = stock - #{quantity}
            WHERE id = #{skuId}
              AND status = 'ENABLED'
              AND stock >= #{quantity}
            """)
    int decrementStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("UPDATE product_sku SET stock = stock + #{quantity} WHERE id = #{skuId}")
    int incrementStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Select("""
            SELECT COUNT(*)
            FROM order_item oi
            INNER JOIN orders o ON o.id = oi.order_id
            WHERE oi.sku_id = #{skuId}
              AND o.status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED')
            """)
    long countActiveOrders(@Param("skuId") Long skuId);
}
