package com.eshop.backend.admin.dashboard;

import java.util.List;

public record SalesTrendResponse(
        int days,
        List<SalesTrendPointResponse> points
) {
}
