package com.eshop.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
        @NotNull(message = "订单商品不能为空")
        @Positive(message = "订单商品编号不正确")
        Long orderItemId,

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低为1分")
        @Max(value = 5, message = "评分最高为5分")
        Integer rating,

        @NotBlank(message = "评价内容不能为空")
        @Size(max = 1000, message = "评价内容不能超过1000个字符")
        String content,

        @Size(max = 3, message = "评价图片最多上传3张")
        List<@Size(max = 500, message = "图片地址过长") String> imageUrls
) {
}
