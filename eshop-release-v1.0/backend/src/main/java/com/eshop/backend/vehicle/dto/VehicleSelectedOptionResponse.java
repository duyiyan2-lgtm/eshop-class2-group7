package com.eshop.backend.vehicle.dto;

import java.math.BigDecimal;

public record VehicleSelectedOptionResponse(
        Long groupId,
        String groupCode,
        String groupName,
        Long valueId,
        String valueCode,
        String valueName,
        BigDecimal priceDelta,
        String colorHex,
        boolean included
) {
}
