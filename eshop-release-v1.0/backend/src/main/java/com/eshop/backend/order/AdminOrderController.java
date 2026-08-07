package com.eshop.backend.order;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.order.dto.OrderResponse;
import com.eshop.backend.order.dto.OrderStatusLogResponse;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResult<OrderResponse>> page(
            @AuthenticationPrincipal LoginUser operator,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {
        return ApiResponse.success(orderService.pageAdminOrders(operator, current, size, status, orderNo));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> detail(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.getAdminOrder(operator, id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<OrderStatusLogResponse>> statusLogs(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.getAdminOrderLogs(operator, id));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<OrderResponse> ship(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.ship(operator, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @AuthenticationPrincipal LoginUser admin,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.cancelByAdmin(admin, id));
    }
}
