package com.eshop.backend.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId,
        @Size(max = 255, message = "订单备注不能超过255个字符")
        String remark
) {
}
