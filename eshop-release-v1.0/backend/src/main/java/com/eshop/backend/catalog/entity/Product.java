package com.eshop.backend.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sellerId;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String detail;
    private String status;
    private String productKind;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
