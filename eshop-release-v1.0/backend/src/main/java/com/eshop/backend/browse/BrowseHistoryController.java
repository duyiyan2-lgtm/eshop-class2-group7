package com.eshop.backend.browse;

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
@RequestMapping("/browse-history")
@RequiredArgsConstructor
public class BrowseHistoryController {
    private final BrowseHistoryService browseHistoryService;

    @PostMapping("/{productId}")
    public ApiResponse<Void> record(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long productId) {
        browseHistoryService.record(user.getUserId(), productId);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<BrowseHistoryProductResponse>> page(
            @AuthenticationPrincipal LoginUser user,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(browseHistoryService.page(user.getUserId(), current, size));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal LoginUser user,
            @PathVariable Long productId) {
        browseHistoryService.remove(user.getUserId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping
    public ApiResponse<Void> clear(@AuthenticationPrincipal LoginUser user) {
        browseHistoryService.clear(user.getUserId());
        return ApiResponse.success();
    }
}
