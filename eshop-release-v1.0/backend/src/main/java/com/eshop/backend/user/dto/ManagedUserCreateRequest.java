package com.eshop.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagedUserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度应为 3 到 50 位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称不能超过 50 位")
        String nickname,

        @Size(max = 20, message = "手机号不能超过 20 位")
        String phone,

        @NotBlank(message = "角色不能为空")
        String role,

        @NotBlank(message = "状态不能为空")
        String status
) {
}
