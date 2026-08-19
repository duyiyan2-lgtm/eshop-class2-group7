package com.eshop.backend.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量至少为1")
        @Max(value = 99, message = "单个SKU最多购买99件")
        Integer quantity,
        java.util.List<Long> optionValueIds
) {
}
