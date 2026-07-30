package com.eshop.backend.admin.dashboard;

import com.eshop.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.success(dashboardService.summary());
    }

    @GetMapping("/sales-trend")
    public ApiResponse<SalesTrendResponse> salesTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return ApiResponse.success(dashboardService.salesTrend(days));
    }

    @GetMapping("/top-products")
    public ApiResponse<TopProductsResponse> topProducts(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ApiResponse.success(dashboardService.topProducts(limit));
    }
}
