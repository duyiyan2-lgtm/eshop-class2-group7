package com.eshop.backend.order.dto;

import java.time.LocalDateTime;

public record OrderStatusLogResponse(
        Long id,
        String fromStatus,
        String toStatus,
        Long operatorId,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
}
