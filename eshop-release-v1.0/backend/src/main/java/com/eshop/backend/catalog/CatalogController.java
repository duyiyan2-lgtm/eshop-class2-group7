package com.eshop.backend.catalog;

import com.eshop.backend.catalog.dto.ProductDetailResponse;
import com.eshop.backend.catalog.dto.ProductSummaryResponse;
import com.eshop.backend.catalog.entity.Category;
import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.success(catalogService.listCategories(true));
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductSummaryResponse>> products(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "12") long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(catalogService.pageProducts(current, size, categoryId, keyword, true));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> product(@PathVariable Long id) {
        return ApiResponse.success(catalogService.getProduct(id, true));
    }
}
