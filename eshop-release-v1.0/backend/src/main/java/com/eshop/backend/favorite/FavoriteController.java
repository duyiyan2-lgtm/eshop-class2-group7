package com.eshop.backend.favorite;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<PageResult<FavoriteProductResponse>> page(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(favoriteService.page(user.getUserId(), current, size));
    }

    @GetMapping("/{productId}/status")
    public ApiResponse<FavoriteStatusResponse> status(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long productId) {
        return ApiResponse.success(favoriteService.status(user.getUserId(), productId));
    }

    @PostMapping("/{productId}")
    public ApiResponse<Void> add(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long productId) {
        favoriteService.add(user.getUserId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long productId) {
        favoriteService.remove(user.getUserId(), productId);
        return ApiResponse.success();
    }
}
