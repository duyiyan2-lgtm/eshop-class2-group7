package com.eshop.backend.admin.dashboard;

import java.util.List;

public record TopProductsResponse(
        int limit,
        List<TopProductItemResponse> items
) {
}
