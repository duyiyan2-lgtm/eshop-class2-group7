package com.eshop.backend.cart;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart_item")
public class CartItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long skuId;
    private Integer quantity;
    private Boolean selected;
    private String configurationHash;
    private String configurationJson;
    private String configurationSummary;
    private java.math.BigDecimal optionAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
