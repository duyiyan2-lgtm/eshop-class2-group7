package com.eshop.backend.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 50, message = "收货人不能超过50个字符")
        String receiverName,
        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "联系电话格式不正确")
        String phone,
        @NotBlank(message = "省份不能为空")
        @Size(max = 50, message = "省份不能超过50个字符")
        String province,
        @NotBlank(message = "城市不能为空")
        @Size(max = 50, message = "城市不能超过50个字符")
        String city,
        @NotBlank(message = "区县不能为空")
        @Size(max = 50, message = "区县不能超过50个字符")
        String district,
        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址不能超过255个字符")
        String detail,
        Boolean isDefault
) {
}
