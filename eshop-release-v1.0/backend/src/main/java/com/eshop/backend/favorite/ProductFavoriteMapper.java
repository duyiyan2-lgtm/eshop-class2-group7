package com.eshop.backend.favorite;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductFavoriteMapper extends BaseMapper<ProductFavorite> {

    @Select("""
            SELECT
                favorite.product_id AS id,
                product.category_id AS category_id,
                product.name,
                product.subtitle,
                product.main_image AS main_image,
                MIN(CASE WHEN sku.status = 'ENABLED' THEN sku.price END) AS min_price,
                COALESCE(SUM(CASE WHEN sku.status = 'ENABLED' THEN sku.stock ELSE 0 END), 0) AS total_stock,
                favorite.created_at AS favorited_at
            FROM product_favorite favorite
            INNER JOIN product ON product.id = favorite.product_id
                AND product.status = 'ON_SALE'
            LEFT JOIN product_sku sku ON sku.product_id = product.id
            WHERE favorite.user_id = #{userId}
            GROUP BY
                favorite.id,
                favorite.product_id,
                product.category_id,
                product.name,
                product.subtitle,
                product.main_image,
                favorite.created_at
            ORDER BY favorite.created_at DESC, favorite.id DESC
            """)
    IPage<FavoriteProductResponse> selectFavoritePage(
            Page<FavoriteProductResponse> page,
            @Param("userId") Long userId);
}
