package com.eshop.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRoleRequest(
        @NotBlank(message = "角色不能为空")
        String role
) {
}
