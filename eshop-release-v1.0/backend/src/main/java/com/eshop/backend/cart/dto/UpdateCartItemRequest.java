package com.eshop.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(value = 1, message = "商品数量至少为1")
        @Max(value = 99, message = "单个SKU最多购买99件")
        Integer quantity,
        Boolean selected
) {
}
