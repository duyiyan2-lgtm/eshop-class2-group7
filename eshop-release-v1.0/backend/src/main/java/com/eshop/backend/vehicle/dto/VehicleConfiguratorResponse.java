package com.eshop.backend.vehicle.dto;

import java.math.BigDecimal;
import java.util.List;

public record VehicleConfiguratorResponse(
        Long productId,
        String productName,
        String subtitle,
        String detail,
        String mainImage,
        String disclaimer,
        Long defaultSkuId,
        List<Long> defaultOptionValueIds,
        List<VehicleSkuCardResponse> skus,
        List<VehicleOptionGroupResponse> groups,
        List<VehicleOptionRuleResponse> rules
) {
    public record VehicleSkuCardResponse(
            Long id,
            String skuCode,
            String versionName,
            String driveType,
            BigDecimal basePrice,
            Integer stock,
            String status
    ) {
    }

    public record VehicleOptionGroupResponse(
            Long id,
            String code,
            String name,
            String selectionType,
            boolean required,
            Integer sortOrder,
            List<VehicleOptionValueCardResponse> values
    ) {
    }

    public record VehicleOptionValueCardResponse(
            Long id,
            String code,
            String name,
            BigDecimal priceDelta,
            String previewImage,
            String colorHex,
            Integer sortOrder
    ) {
    }

    public record VehicleOptionRuleResponse(
            Long skuId,
            Long optionValueId,
            boolean available,
            boolean included,
            BigDecimal priceOverride
    ) {
    }
}
