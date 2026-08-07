package com.eshop.backend.admin.inventory;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {
    private final InventoryAlertMapper inventoryAlertMapper;

    @Transactional(readOnly = true)
    public PageResult<InventoryAlertResponse> page(
            LoginUser operator,
            long current,
            long size,
            int threshold,
            String keyword) {
        int safeThreshold = Math.min(Math.max(threshold, 0), 100000);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<InventoryAlertResponse> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 100));
        return PageResult.from(inventoryAlertMapper.selectAlertPage(
                page,
                safeThreshold,
                normalizedKeyword,
                sellerId(operator)));
    }

    private Long sellerId(LoginUser operator) {
        return operator != null && "SELLER".equals(operator.getRole())
                ? operator.getUserId()
                : null;
    }
}
