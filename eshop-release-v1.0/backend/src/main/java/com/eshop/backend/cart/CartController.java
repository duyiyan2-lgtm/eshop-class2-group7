package com.eshop.backend.cart;

import com.eshop.backend.cart.dto.AddCartItemRequest;
import com.eshop.backend.cart.dto.CartItemResponse;
import com.eshop.backend.cart.dto.UpdateCartItemRequest;
import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ApiResponse<List<CartItemResponse>> list(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.success(cartService.list(user.getUserId()));
    }

    @PostMapping
    public ApiResponse<CartItemResponse> add(
            @AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.success(cartService.add(user.getUserId(), request));
    }

    @PatchMapping("/{itemId}")
    public ApiResponse<CartItemResponse> update(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.success(cartService.update(user.getUserId(), itemId, request));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long itemId) {
        cartService.remove(user.getUserId(), itemId);
        return ApiResponse.success();
    }
}
