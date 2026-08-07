package com.eshop.backend.catalog;

import com.eshop.backend.catalog.dto.CategoryRequest;
import com.eshop.backend.catalog.dto.ProductDetailResponse;
import com.eshop.backend.catalog.dto.ProductRequest;
import com.eshop.backend.catalog.dto.ProductSummaryResponse;
import com.eshop.backend.catalog.dto.SkuRequest;
import com.eshop.backend.catalog.dto.SkuResponse;
import com.eshop.backend.catalog.dto.StatusRequest;
import com.eshop.backend.catalog.dto.StockUpdateRequest;
import com.eshop.backend.catalog.entity.Category;
import com.eshop.backend.common.ApiResponse;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
public class AdminCatalogController {
    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.success(catalogService.listCategories(false));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Category> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(catalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Category> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(catalogService.updateCategory(id, request));
    }

    @PatchMapping("/categories/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Category> updateCategoryStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(catalogService.updateCategoryStatus(id, request.status()));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ApiResponse.success();
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductSummaryResponse>> products(
            @AuthenticationPrincipal LoginUser operator,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(catalogService.pageManagedProducts(
                operator, current, size, categoryId, keyword));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> product(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        return ApiResponse.success(catalogService.getManagedProduct(operator, id));
    }

    @PostMapping("/products")
    public ApiResponse<ProductDetailResponse> createProduct(
            @AuthenticationPrincipal LoginUser operator,
            @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(catalogService.createProduct(operator, request));
    }

    @PutMapping("/products/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(catalogService.updateProduct(operator, id, request));
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<ProductDetailResponse> updateProductStatus(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(catalogService.updateProductStatus(operator, id, request.status()));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> deleteProduct(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        catalogService.deleteProduct(operator, id);
        return ApiResponse.success();
    }

    @PostMapping("/products/{productId}/skus")
    public ApiResponse<SkuResponse> createSku(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long productId,
            @Valid @RequestBody SkuRequest request) {
        return ApiResponse.success(catalogService.createSku(operator, productId, request));
    }

    @PutMapping("/skus/{id}")
    public ApiResponse<SkuResponse> updateSku(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id,
            @Valid @RequestBody SkuRequest request) {
        return ApiResponse.success(catalogService.updateSku(operator, id, request));
    }

    @PatchMapping("/skus/{id}/stock")
    public ApiResponse<SkuResponse> updateStock(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id,
            @Valid @RequestBody StockUpdateRequest request) {
        return ApiResponse.success(catalogService.updateStock(operator, id, request.stock()));
    }

    @DeleteMapping("/skus/{id}")
    public ApiResponse<Void> deleteSku(
            @AuthenticationPrincipal LoginUser operator,
            @PathVariable Long id) {
        catalogService.deleteSku(operator, id);
        return ApiResponse.success();
    }
}
