package com.eshop.backend.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentNo,
        String orderNo,
        BigDecimal amount,
        String status,
        LocalDateTime paidAt
) {
}
