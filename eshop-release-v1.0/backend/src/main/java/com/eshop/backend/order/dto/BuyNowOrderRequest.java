package com.eshop.backend.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BuyNowOrderRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId,
        @NotNull(message = "SKU不能为空")
        Long skuId,
        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量至少为1")
        @Max(value = 99, message = "单个SKU最多购买99件")
        Integer quantity,
        @Size(max = 255, message = "订单备注不能超过255个字符")
        String remark
) {
}
