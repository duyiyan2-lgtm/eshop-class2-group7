package com.eshop.backend.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SkuRequest(
        @NotBlank(message = "SKU编码不能为空")
        @Size(max = 64, message = "SKU编码不能超过64个字符")
        String skuCode,
        @NotBlank(message = "SKU规格不能为空")
        @Size(max = 1000, message = "SKU规格不能超过1000个字符")
        String specsJson,
        @NotNull(message = "SKU价格不能为空")
        @DecimalMin(value = "0.01", message = "SKU价格必须大于0")
        BigDecimal price,
        @NotNull(message = "SKU库存不能为空")
        @Min(value = 0, message = "SKU库存不能小于0")
        Integer stock,
        @Pattern(regexp = "ENABLED|DISABLED", message = "SKU状态不正确")
        String status
) {
}
