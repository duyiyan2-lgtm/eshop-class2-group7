package com.eshop.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称不能超过50个字符")
        String nickname,
        @Pattern(regexp = "^$|^[0-9+\\- ]{6,20}$", message = "联系电话格式不正确")
        String phone
) {
}
