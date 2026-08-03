package com.eshop.backend.admin.inventory;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InventoryAlertMapper {

    @Select("""
            SELECT
                sku.id AS sku_id,
                product.id AS product_id,
                product.name AS product_name,
                product.main_image AS main_image,
                sku.sku_code AS sku_code,
                sku.specs_json AS specs_json,
                sku.price,
                sku.stock,
                sku.status AS sku_status,
                product.status AS product_status
            FROM product_sku sku
            INNER JOIN product ON product.id = sku.product_id
            WHERE product.status = 'ON_SALE'
              AND sku.status = 'ENABLED'
              AND sku.stock <= #{threshold}
              AND (
                  #{keyword} IS NULL
                  OR LOWER(product.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(sku.sku_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            ORDER BY sku.stock ASC, product.id DESC, sku.id DESC
            """)
    IPage<InventoryAlertResponse> selectAlertPage(
            Page<InventoryAlertResponse> page,
            @Param("threshold") int threshold,
            @Param("keyword") String keyword);
}
