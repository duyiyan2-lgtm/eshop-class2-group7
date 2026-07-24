package com.eshop.backend.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotNull(message = "商品分类不能为空")
        Long categoryId,
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 120, message = "商品名称不能超过120个字符")
        String name,
        @Size(max = 255, message = "商品副标题不能超过255个字符")
        String subtitle,
        @Size(max = 500, message = "商品主图地址不能超过500个字符")
        String mainImage,
        String detail,
        @Pattern(regexp = "DRAFT|ON_SALE|OFF_SALE", message = "商品状态不正确")
        String status
) {
}
