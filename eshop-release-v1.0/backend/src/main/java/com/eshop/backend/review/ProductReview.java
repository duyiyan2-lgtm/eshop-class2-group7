package com.eshop.backend.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_review")
public class ProductReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Long orderItemId;
    private Integer rating;
    private String content;
    private String imagesJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
