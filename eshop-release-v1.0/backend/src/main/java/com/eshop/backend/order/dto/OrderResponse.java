package com.eshop.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNo,
        Long userId,
        BigDecimal totalAmount,
        String status,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
