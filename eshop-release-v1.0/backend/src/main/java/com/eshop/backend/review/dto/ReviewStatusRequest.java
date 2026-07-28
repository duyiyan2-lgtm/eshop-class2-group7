package com.eshop.backend.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewStatusRequest(
        @NotBlank
        @Pattern(regexp = "PUBLISHED|HIDDEN", message = "评价状态只能是 PUBLISHED 或 HIDDEN")
        String status) {
}
