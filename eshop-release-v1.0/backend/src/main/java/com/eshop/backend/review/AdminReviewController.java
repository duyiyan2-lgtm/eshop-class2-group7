package com.eshop.backend.review;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.review.dto.AdminReviewResponse;
import com.eshop.backend.review.dto.ReviewStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {
    private final ReviewService reviewService;

    @GetMapping
    public ApiResponse<PageResult<AdminReviewResponse>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(reviewService.pageAdmin(current, size, keyword, rating, status));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminReviewResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ReviewStatusRequest request) {
        return ApiResponse.success(reviewService.updateStatus(id, request.status()));
    }
}
