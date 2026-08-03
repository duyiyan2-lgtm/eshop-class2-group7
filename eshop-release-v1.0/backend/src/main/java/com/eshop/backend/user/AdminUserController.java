package com.eshop.backend.user;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import com.eshop.backend.user.dto.AdminUserResponse;
import com.eshop.backend.user.dto.AdminUserSummaryResponse;
import com.eshop.backend.user.dto.ManagedUserCreateRequest;
import com.eshop.backend.user.dto.UserRoleRequest;
import com.eshop.backend.user.dto.UserStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminUserService.page(current, size, keyword, role, status));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminUserSummaryResponse> summary() {
        return ApiResponse.success(adminUserService.summary());
    }

    @PostMapping
    public ApiResponse<AdminUserResponse> create(@Valid @RequestBody ManagedUserCreateRequest request) {
        return ApiResponse.success(adminUserService.create(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal LoginUser operator) {
        return ApiResponse.success(adminUserService.updateStatus(id, request.status(), operator.getUserId()));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<AdminUserResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleRequest request,
            @AuthenticationPrincipal LoginUser operator) {
        return ApiResponse.success(adminUserService.updateRole(id, request.role(), operator.getUserId()));
    }
}
