package com.eshop.backend.admin.dashboard;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(
            @AuthenticationPrincipal LoginUser operator) {
        return ApiResponse.success(dashboardService.summary(operator));
    }

    @GetMapping("/sales-trend")
    public ApiResponse<SalesTrendResponse> salesTrend(
            @AuthenticationPrincipal LoginUser operator,
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return ApiResponse.success(dashboardService.salesTrend(operator, days));
    }

    @GetMapping("/top-products")
    public ApiResponse<TopProductsResponse> topProducts(
            @AuthenticationPrincipal LoginUser operator,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ApiResponse.success(dashboardService.topProducts(operator, limit));
    }
}
