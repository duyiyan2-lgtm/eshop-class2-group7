package com.eshop.backend.review;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.review.dto.CreateReviewRequest;
import com.eshop.backend.review.dto.MyReviewResponse;
import com.eshop.backend.review.dto.ProductReviewResponse;
import com.eshop.backend.review.dto.ProductReviewSummaryResponse;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<PageResult<ProductReviewResponse>> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(reviewService.pageByProduct(productId, current, size));
    }

    @GetMapping("/products/{productId}/reviews/summary")
    public ApiResponse<ProductReviewSummaryResponse> productReviewSummary(
            @PathVariable Long productId) {
        return ApiResponse.success(reviewService.summaryByProduct(productId));
    }

    @PostMapping("/reviews")
    public ApiResponse<MyReviewResponse> create(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.success(reviewService.create(user.getUserId(), request));
    }

    @GetMapping("/reviews/mine")
    public ApiResponse<PageResult<MyReviewResponse>> mine(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(reviewService.pageMine(user.getUserId(), current, size));
    }
}
