package com.eshop.backend.admin.operationlog;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
public class OperationLogController {
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResponse<PageResult<OperationLog>> page(
            @AuthenticationPrincipal LoginUser operator,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(operationLogService.page(operator, current, size));
    }
}
