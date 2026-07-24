package com.eshop.backend.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能小于0")
        Integer stock
) {
}
