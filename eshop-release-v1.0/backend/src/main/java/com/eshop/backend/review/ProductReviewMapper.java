package com.eshop.backend.review;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.review.dto.ProductReviewRow;
import com.eshop.backend.review.dto.ProductReviewSummaryResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    @Select("""
            <script>
            SELECT
                review.id,
                review.user_id AS user_id,
                reviewer.username,
                COALESCE(NULLIF(TRIM(reviewer.nickname), ''), '匿名用户') AS reviewer_nickname,
                review.product_id AS product_id,
                item.product_name AS product_name,
                item.sku_specs AS sku_specs,
                review.rating,
                review.content,
                review.images_json AS images_json,
                review.status,
                review.created_at AS created_at,
                review.updated_at AS updated_at
            FROM product_review review
            INNER JOIN order_item item ON item.id = review.order_item_id
            INNER JOIN product reviewed_product ON reviewed_product.id = review.product_id
            LEFT JOIN sys_user reviewer ON reviewer.id = review.user_id
            WHERE 1 = 1
            <if test="sellerId != null">
              AND reviewed_product.seller_id = #{sellerId}
            </if>
            <if test="status != null and status != ''">
              AND review.status = #{status}
            </if>
            <if test="rating != null">
              AND review.rating = #{rating}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (
                LOWER(item.product_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(COALESCE(reviewer.username, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(COALESCE(reviewer.nickname, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(review.content) LIKE CONCAT('%', LOWER(#{keyword}), '%')
              )
            </if>
            ORDER BY review.created_at DESC, review.id DESC
            </script>
            """)
    IPage<ProductReviewRow> selectAdminReviewPage(
            Page<ProductReviewRow> page,
            @Param("keyword") String keyword,
            @Param("rating") Integer rating,
            @Param("status") String status,
            @Param("sellerId") Long sellerId);

    @Select("""
            SELECT
                review.id,
                review.user_id AS user_id,
                reviewer.username,
                COALESCE(NULLIF(TRIM(reviewer.nickname), ''), '匿名用户') AS reviewer_nickname,
                review.product_id AS product_id,
                item.product_name AS product_name,
                item.sku_specs AS sku_specs,
                review.rating,
                review.content,
                review.images_json AS images_json,
                review.status,
                review.created_at AS created_at,
                review.updated_at AS updated_at
            FROM product_review review
            INNER JOIN order_item item ON item.id = review.order_item_id
            LEFT JOIN sys_user reviewer ON reviewer.id = review.user_id
            WHERE review.id = #{id}
            """)
    ProductReviewRow selectAdminReviewById(@Param("id") Long id);

    @Select("""
            SELECT
                review.id,
                review.product_id AS product_id,
                item.product_name AS product_name,
                item.sku_specs AS sku_specs,
                review.rating,
                review.content,
                review.images_json AS images_json,
                COALESCE(NULLIF(TRIM(reviewer.nickname), ''), '匿名用户') AS reviewer_nickname,
                review.created_at AS created_at
            FROM product_review review
            INNER JOIN order_item item ON item.id = review.order_item_id
            LEFT JOIN sys_user reviewer ON reviewer.id = review.user_id
            WHERE review.product_id = #{productId}
              AND review.status = 'PUBLISHED'
            ORDER BY review.created_at DESC, review.id DESC
            """)
    IPage<ProductReviewRow> selectProductReviewPage(
            Page<ProductReviewRow> page,
            @Param("productId") Long productId);

    @Select("""
            SELECT
                review.id,
                review.product_id AS product_id,
                review.order_id AS order_id,
                review.order_item_id AS order_item_id,
                item.product_name AS product_name,
                item.sku_specs AS sku_specs,
                item.product_image AS product_image,
                review.rating,
                review.content,
                review.images_json AS images_json,
                review.status,
                review.created_at AS created_at,
                review.updated_at AS updated_at
            FROM product_review review
            INNER JOIN order_item item ON item.id = review.order_item_id
            WHERE review.user_id = #{userId}
            ORDER BY review.created_at DESC, review.id DESC
            """)
    IPage<ProductReviewRow> selectMyReviewPage(
            Page<ProductReviewRow> page,
            @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT order_item_id
            FROM product_review
            WHERE user_id = #{userId}
              AND order_item_id IN
              <foreach collection="orderItemIds" item="orderItemId" open="(" separator="," close=")">
                #{orderItemId}
              </foreach>
            </script>
            """)
    List<Long> selectReviewedOrderItemIds(
            @Param("userId") Long userId,
            @Param("orderItemIds") List<Long> orderItemIds);

    @Select("""
            SELECT
                COALESCE(ROUND(AVG(rating), 1), 0) AS average_rating,
                COUNT(*) AS total,
                COALESCE(SUM(CASE WHEN rating = 5 THEN 1 ELSE 0 END), 0) AS five_star_count,
                COALESCE(SUM(CASE WHEN rating = 4 THEN 1 ELSE 0 END), 0) AS four_star_count,
                COALESCE(SUM(CASE WHEN rating = 3 THEN 1 ELSE 0 END), 0) AS three_star_count,
                COALESCE(SUM(CASE WHEN rating = 2 THEN 1 ELSE 0 END), 0) AS two_star_count,
                COALESCE(SUM(CASE WHEN rating = 1 THEN 1 ELSE 0 END), 0) AS one_star_count
            FROM product_review
            WHERE product_id = #{productId}
              AND status = 'PUBLISHED'
            """)
    ProductReviewSummaryResponse selectProductReviewSummary(@Param("productId") Long productId);
}
