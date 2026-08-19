package com.eshop.backend.vehicle.dto;

import java.math.BigDecimal;
import java.util.List;

public record VehicleQuoteResponse(
        Long productId,
        String productName,
        Long skuId,
        String skuCode,
        String versionName,
        List<VehicleSelectedOptionResponse> selectedOptions,
        String configurationSummary,
        String configurationJson,
        String configurationHash,
        BigDecimal basePrice,
        BigDecimal optionAmount,
        BigDecimal unitPrice,
        Integer stock,
        boolean purchasable,
        String unavailableReason
) {
}
