package com.eshop.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 50, message = "用户名长度应为 3 到 50 位") String username,
        @NotBlank(message = "密码不能为空") @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String password,
        @NotBlank(message = "昵称不能为空") @Size(max = 50, message = "昵称不能超过 50 位") String nickname
) {
}
