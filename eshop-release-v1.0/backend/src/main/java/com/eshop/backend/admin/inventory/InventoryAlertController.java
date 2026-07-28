package com.eshop.backend.admin.inventory;

import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InventoryAlertController {
    private final InventoryAlertService inventoryAlertService;

    @GetMapping("/alerts")
    public ApiResponse<PageResult<InventoryAlertResponse>> alerts(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "10") int threshold,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(inventoryAlertService.page(
                current,
                size,
                threshold,
                keyword));
    }
}
