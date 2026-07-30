package com.eshop.backend.browse;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductBrowseHistoryMapper extends BaseMapper<ProductBrowseHistory> {

    @Select("""
            SELECT
                history.product_id AS product_id,
                product.name,
                product.subtitle,
                product.main_image AS main_image,
                MIN(CASE WHEN sku.status = 'ENABLED' THEN sku.price END) AS min_price,
                COALESCE(SUM(CASE WHEN sku.status = 'ENABLED' THEN sku.stock ELSE 0 END), 0) AS total_stock,
                history.browsed_at AS browsed_at
            FROM product_browse_history history
            INNER JOIN product ON product.id = history.product_id
                AND product.status = 'ON_SALE'
            LEFT JOIN product_sku sku ON sku.product_id = product.id
            WHERE history.user_id = #{userId}
            GROUP BY
                history.id,
                history.product_id,
                product.name,
                product.subtitle,
                product.main_image,
                history.browsed_at
            ORDER BY history.browsed_at DESC, history.id DESC
            """)
    IPage<BrowseHistoryProductResponse> selectBrowsePage(
            Page<BrowseHistoryProductResponse> page,
            @Param("userId") Long userId);
}
