package com.eshop.backend.vehicle.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VehicleQuoteRequest(
        @NotNull(message = "车辆版本不能为空")
        Long skuId,
        List<Long> optionValueIds
) {
}
