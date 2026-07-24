package com.eshop.backend.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.cart.CartItem;
import com.eshop.backend.cart.CartItemMapper;
import com.eshop.backend.catalog.dto.CategoryRequest;
import com.eshop.backend.catalog.dto.ProductDetailResponse;
import com.eshop.backend.catalog.dto.ProductRequest;
import com.eshop.backend.catalog.dto.ProductSummaryResponse;
import com.eshop.backend.catalog.dto.SkuRequest;
import com.eshop.backend.catalog.dto.SkuResponse;
import com.eshop.backend.catalog.entity.Category;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.entity.ProductSku;
import com.eshop.backend.catalog.mapper.CategoryMapper;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.catalog.mapper.ProductSkuMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final CartItemMapper cartItemMapper;
    private final ObjectMapper objectMapper;

    public List<Category> listCategories(boolean enabledOnly) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(enabledOnly, Category::getStatus, "ENABLED")
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    @Transactional
    @OperationLogAction(module = "商品分类", action = "新增分类")
    public Category createCategory(CategoryRequest request) {
        validateParent(null, request.parentId());
        String targetStatus = request.status() == null ? "ENABLED" : request.status();
        ensureCategoryCanChangeTo(null, request.parentId(), targetStatus);
        ensureCategoryNameAvailable(null, request.parentId(), request.name());
        Category category = new Category();
        copyCategory(category, request);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    @OperationLogAction(module = "商品分类", action = "修改分类")
    public Category updateCategory(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        validateParent(id, request.parentId());
        ensureCategoryNameAvailable(id, request.parentId(), request.name());
        String targetStatus = request.status() == null ? category.getStatus() : request.status();
        ensureCategoryCanChangeTo(id, request.parentId(), targetStatus);
        copyCategory(category, request);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    @OperationLogAction(module = "商品分类", action = "修改分类状态")
    public Category updateCategoryStatus(Long id, String status) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Category category = requireCategory(id);
        ensureCategoryCanChangeTo(id, category.getParentId(), status);
        category.setStatus(status);
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    @OperationLogAction(module = "商品分类", action = "删除分类")
    public void deleteCategory(Long id) {
        requireCategory(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if ((childCount != null && childCount > 0) || (productCount != null && productCount > 0)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY);
        }
        categoryMapper.deleteById(id);
    }

    public PageResult<ProductSummaryResponse> pageProducts(
            long current, long size, Long categoryId, String keyword, boolean publicOnly) {
        Page<Product> page = productMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<Product>()
                        .eq(categoryId != null, Product::getCategoryId, categoryId)
                        .like(StringUtils.hasText(keyword), Product::getName, keyword)
                        .eq(publicOnly, Product::getStatus, "ON_SALE")
                        .orderByDesc(Product::getCreatedAt));
        List<ProductSummaryResponse> records = page.getRecords().stream()
                .map(product -> toSummary(product, publicOnly))
                .toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    public ProductDetailResponse getProduct(Long id, boolean publicOnly) {
        Product product = productMapper.selectById(id);
        if (product == null || (publicOnly && !"ON_SALE".equals(product.getStatus()))) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Category category = categoryMapper.selectById(product.getCategoryId());
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, id)
                .eq(publicOnly, ProductSku::getStatus, "ENABLED")
                .orderByAsc(ProductSku::getPrice)
                .orderByAsc(ProductSku::getId));
        return new ProductDetailResponse(
                product.getId(),
                product.getCategoryId(),
                category == null ? null : category.getName(),
                product.getName(),
                product.getSubtitle(),
                product.getMainImage(),
                product.getDetail(),
                product.getStatus(),
                skus.stream().map(this::toSkuResponse).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    @Transactional
    @OperationLogAction(module = "商品管理", action = "新增商品")
    public ProductDetailResponse createProduct(ProductRequest request) {
        requireEnabledCategory(request.categoryId());
        if ("ON_SALE".equals(request.status())) {
            throw new BusinessException(ErrorCode.PRODUCT_REQUIRES_SKU);
        }
        Product product = new Product();
        copyProduct(product, request);
        productMapper.insert(product);
        return getProduct(product.getId(), false);
    }

    @Transactional
    @OperationLogAction(module = "商品管理", action = "修改商品")
    public ProductDetailResponse updateProduct(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        requireEnabledCategory(request.categoryId());
        String targetStatus = request.status() == null ? product.getStatus() : request.status();
        ensureProductCanBeOnSale(product.getId(), targetStatus);
        copyProduct(product, request);
        productMapper.updateById(product);
        return getProduct(id, false);
    }

    @Transactional
    @OperationLogAction(module = "商品管理", action = "修改商品状态")
    public ProductDetailResponse updateProductStatus(Long id, String status) {
        if (!List.of("DRAFT", "ON_SALE", "OFF_SALE").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Product product = requireProduct(id);
        if ("ON_SALE".equals(status)) {
            requireEnabledCategory(product.getCategoryId());
        }
        ensureProductCanBeOnSale(id, status);
        product.setStatus(status);
        productMapper.updateById(product);
        return getProduct(id, false);
    }

    @Transactional
    @OperationLogAction(module = "商品管理", action = "删除商品")
    public void deleteProduct(Long id) {
        Product product = requireProduct(id);
        if ("ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_MUST_BE_OFF_SALE);
        }
        if (productMapper.countActiveOrders(id) > 0) {
            throw new BusinessException(ErrorCode.PRODUCT_HAS_ACTIVE_ORDERS);
        }
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, id));
        List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();
        if (!skuIds.isEmpty()) {
            cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().in(CartItem::getSkuId, skuIds));
            skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        }
        productMapper.deleteById(id);
    }

    @Transactional
    @OperationLogAction(module = "SKU管理", action = "新增SKU")
    public SkuResponse createSku(Long productId, SkuRequest request) {
        requireProduct(productId);
        validateSpecs(request.specsJson());
        ensureSkuCodeAvailable(null, request.skuCode());
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        copySku(sku, request);
        skuMapper.insert(sku);
        return toSkuResponse(sku);
    }

    @Transactional
    @OperationLogAction(module = "SKU管理", action = "修改SKU")
    public SkuResponse updateSku(Long id, SkuRequest request) {
        ProductSku sku = requireSku(id);
        validateSpecs(request.specsJson());
        ensureSkuCodeAvailable(id, request.skuCode());
        String targetStatus = request.status() == null ? sku.getStatus() : request.status();
        ensureSkuCanChangeTo(sku, targetStatus);
        copySku(sku, request);
        skuMapper.updateById(sku);
        return toSkuResponse(sku);
    }

    @Transactional
    @OperationLogAction(module = "SKU管理", action = "调整库存")
    public SkuResponse updateStock(Long id, int stock) {
        ProductSku sku = requireSku(id);
        sku.setStock(stock);
        skuMapper.updateById(sku);
        return toSkuResponse(sku);
    }

    @Transactional
    @OperationLogAction(module = "SKU管理", action = "删除SKU")
    public void deleteSku(Long id) {
        ProductSku sku = requireSku(id);
        Product product = requireProduct(sku.getProductId());
        if ("ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_MUST_BE_OFF_SALE);
        }
        if (skuMapper.countActiveOrders(id) > 0) {
            throw new BusinessException(ErrorCode.SKU_HAS_ACTIVE_ORDERS);
        }
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getSkuId, id));
        skuMapper.deleteById(id);
    }

    public Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    public ProductSku requireSku(Long id) {
        ProductSku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku;
    }

    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private void requireEnabledCategory(Long id) {
        Category category = requireCategory(id);
        if (!"ENABLED".equals(category.getStatus())) {
            throw new BusinessException(ErrorCode.CATEGORY_DISABLED);
        }
    }

    private void validateParent(Long currentId, Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (cursor.equals(currentId) || !visited.add(cursor)) {
                throw new BusinessException(ErrorCode.CATEGORY_CYCLE);
            }
            Category parent = requireCategory(cursor);
            cursor = parent.getParentId();
        }
    }

    private void ensureCategoryCanChangeTo(Long categoryId, Long parentId, String targetStatus) {
        if ("ENABLED".equals(targetStatus)) {
            ensureParentChainEnabled(parentId);
            return;
        }
        if (categoryId == null || !"DISABLED".equals(targetStatus)) {
            return;
        }
        Long onSaleProducts = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, categoryId)
                .eq(Product::getStatus, "ON_SALE"));
        if (onSaleProducts != null && onSaleProducts > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_ON_SALE_PRODUCTS);
        }
        if (hasEnabledDescendant(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_ENABLED_CHILDREN);
        }
    }

    private void ensureParentChainEnabled(Long parentId) {
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null) {
            if (!visited.add(cursor)) {
                throw new BusinessException(ErrorCode.CATEGORY_CYCLE);
            }
            Category parent = requireCategory(cursor);
            if (!"ENABLED".equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.CATEGORY_PARENT_DISABLED);
            }
            cursor = parent.getParentId();
        }
    }

    private boolean hasEnabledDescendant(Long categoryId) {
        List<Category> categories = categoryMapper.selectList(null);
        Set<Long> descendants = new HashSet<>();
        descendants.add(categoryId);
        boolean changed;
        do {
            changed = false;
            for (Category category : categories) {
                if (category.getParentId() != null
                        && descendants.contains(category.getParentId())
                        && descendants.add(category.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return categories.stream()
                .anyMatch(category -> !category.getId().equals(categoryId)
                        && descendants.contains(category.getId())
                        && "ENABLED".equals(category.getStatus()));
    }

    private void ensureProductCanBeOnSale(Long productId, String targetStatus) {
        if (!"ON_SALE".equals(targetStatus)) {
            return;
        }
        Long enabledSkuCount = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getStatus, "ENABLED"));
        if (enabledSkuCount == null || enabledSkuCount == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_REQUIRES_SKU);
        }
    }

    private void ensureSkuCanChangeTo(ProductSku sku, String targetStatus) {
        if (!"DISABLED".equals(targetStatus)) {
            return;
        }
        Product product = requireProduct(sku.getProductId());
        if (!"ON_SALE".equals(product.getStatus())) {
            return;
        }
        Long otherEnabledSkuCount = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, sku.getProductId())
                .eq(ProductSku::getStatus, "ENABLED")
                .ne(ProductSku::getId, sku.getId()));
        if (otherEnabledSkuCount == null || otherEnabledSkuCount == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_REQUIRES_ENABLED_SKU);
        }
    }

    private void ensureCategoryNameAvailable(Long currentId, Long parentId, String name) {
        LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name.trim());
        if (parentId == null) {
            query.isNull(Category::getParentId);
        } else {
            query.eq(Category::getParentId, parentId);
        }
        query.ne(currentId != null, Category::getId, currentId);
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
    }

    private void ensureSkuCodeAvailable(Long currentId, String skuCode) {
        Long count = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuCode, skuCode.trim())
                .ne(currentId != null, ProductSku::getId, currentId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.SKU_CODE_EXISTS);
        }
    }

    private void validateSpecs(String specsJson) {
        try {
            JsonNode node = objectMapper.readTree(specsJson);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("not an object");
            }
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_SKU_SPECS);
        }
    }

    private void copyCategory(Category category, CategoryRequest request) {
        category.setParentId(request.parentId());
        category.setName(request.name().trim());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        if (request.status() != null) {
            category.setStatus(request.status());
        } else if (category.getId() == null) {
            category.setStatus("ENABLED");
        }
    }

    private void copyProduct(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        product.setSubtitle(request.subtitle());
        product.setMainImage(request.mainImage());
        product.setDetail(request.detail());
        if (request.status() != null) {
            product.setStatus(request.status());
        } else if (product.getId() == null) {
            product.setStatus("DRAFT");
        }
    }

    private void copySku(ProductSku sku, SkuRequest request) {
        sku.setSkuCode(request.skuCode().trim());
        sku.setSpecsJson(request.specsJson());
        sku.setPrice(request.price());
        sku.setStock(request.stock());
        if (request.status() != null) {
            sku.setStatus(request.status());
        } else if (sku.getId() == null) {
            sku.setStatus("ENABLED");
        }
    }

    private ProductSummaryResponse toSummary(Product product, boolean publicOnly) {
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, product.getId())
                .eq(publicOnly, ProductSku::getStatus, "ENABLED"));
        BigDecimal minPrice = skus.stream()
                .map(ProductSku::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
        int totalStock = skus.stream().mapToInt(ProductSku::getStock).sum();
        return new ProductSummaryResponse(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getSubtitle(),
                product.getMainImage(),
                product.getStatus(),
                minPrice,
                totalStock,
                product.getCreatedAt());
    }

    private SkuResponse toSkuResponse(ProductSku sku) {
        return new SkuResponse(
                sku.getId(),
                sku.getProductId(),
                sku.getSkuCode(),
                sku.getSpecsJson(),
                sku.getPrice(),
                sku.getStock(),
                sku.getStatus());
    }
}
