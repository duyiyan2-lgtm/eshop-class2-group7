package com.eshop.backend.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        Long parentId,
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 50, message = "分类名称不能超过50个字符")
        String name,
        @Min(value = 0, message = "排序值不能小于0")
        Integer sortOrder,
        @Pattern(regexp = "ENABLED|DISABLED", message = "分类状态不正确")
        String status
) {
}
