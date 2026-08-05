package com.eshop.backend.order;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.order.dto.BuyNowOrderRequest;
import com.eshop.backend.order.dto.CreateOrderRequest;
import com.eshop.backend.order.dto.OrderResponse;
import com.eshop.backend.order.dto.OrderStatusLogResponse;
import com.eshop.backend.order.dto.PaymentResponse;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> create(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(user, request));
    }

    @PostMapping("/buy-now")
    public ApiResponse<OrderResponse> buyNow(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody BuyNowOrderRequest request) {
        return ApiResponse.success(orderService.buyNow(user, request));
    }

    @GetMapping
    public ApiResponse<PageResult<OrderResponse>> page(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(orderService.pageUserOrders(user.getUserId(), current, size, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> detail(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.getUserOrder(user.getUserId(), id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<OrderStatusLogResponse>> statusLogs(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.getUserOrderLogs(user.getUserId(), id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<PaymentResponse> pay(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.pay(user, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.cancel(user, id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<OrderResponse> confirm(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long id) {
        return ApiResponse.success(orderService.confirm(user, id));
    }
}
