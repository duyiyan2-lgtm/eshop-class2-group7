package com.eshop.backend.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.order.OrderItem;
import com.eshop.backend.order.OrderItemMapper;
import com.eshop.backend.order.OrderService;
import com.eshop.backend.order.ShopOrder;
import com.eshop.backend.order.ShopOrderMapper;
import com.eshop.backend.review.dto.AdminReviewResponse;
import com.eshop.backend.review.dto.CreateReviewRequest;
import com.eshop.backend.review.dto.MyReviewResponse;
import com.eshop.backend.review.dto.ProductReviewResponse;
import com.eshop.backend.review.dto.ProductReviewRow;
import com.eshop.backend.review.dto.ProductReviewSummaryResponse;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final String HIDDEN = "HIDDEN";
    private static final Set<String> ADMIN_STATUSES = Set.of(PUBLISHED, HIDDEN);

    private final ProductReviewMapper reviewMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShopOrderMapper orderMapper;
    private final ProductMapper productMapper;

    @Transactional
    public MyReviewResponse create(Long userId, CreateReviewRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(content) || content.length() > 1000) {
            throw new BusinessException(ErrorCode.REVIEW_CONTENT_INVALID);
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new BusinessException(ErrorCode.REVIEW_RATING_INVALID);
        }
        String imagesJson = ReviewImages.toJson(request.imageUrls());

        OrderItem item = orderItemMapper.selectById(request.orderItemId());
        if (item == null) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }
        ShopOrder order = orderMapper.selectById(item.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }
        if (!OrderService.COMPLETED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_COMPLETED_FOR_REVIEW);
        }
        Long existing = reviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getOrderItemId, item.getId()));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        ProductReview review = new ProductReview();
        review.setUserId(userId);
        review.setProductId(item.getProductId());
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setRating(request.rating());
        review.setContent(content);
        review.setImagesJson(imagesJson);
        review.setStatus(PUBLISHED);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        return toMyResponse(review, item);
    }

    @Transactional(readOnly = true)
    public PageResult<ProductReviewResponse> pageByProduct(Long productId, long current, long size) {
        requirePublicProduct(productId);
        Page<ProductReviewRow> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 50));
        IPage<ProductReviewRow> result = reviewMapper.selectProductReviewPage(page, productId);
        return mapPage(result, this::toProductResponse);
    }

    @Transactional(readOnly = true)
    public ProductReviewSummaryResponse summaryByProduct(Long productId) {
        requirePublicProduct(productId);
        return reviewMapper.selectProductReviewSummary(productId);
    }

    @Transactional(readOnly = true)
    public PageResult<MyReviewResponse> pageMine(Long userId, long current, long size) {
        Page<ProductReviewRow> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 50));
        IPage<ProductReviewRow> result = reviewMapper.selectMyReviewPage(page, userId);
        return mapPage(result, this::toMyResponse);
    }

    @Transactional(readOnly = true)
    public PageResult<AdminReviewResponse> pageAdmin(
            LoginUser operator,
            long current,
            long size,
            String keyword,
            Integer rating,
            String status) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        if (normalizedStatus != null && !ADMIN_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException(ErrorCode.REVIEW_RATING_INVALID);
        }
        Page<ProductReviewRow> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 100));
        IPage<ProductReviewRow> result = reviewMapper.selectAdminReviewPage(
                page,
                normalizedKeyword,
                rating,
                normalizedStatus,
                sellerId(operator));
        return mapPage(result, this::toAdminResponse);
    }

    @Transactional
    @OperationLogAction(module = "评价管理", action = "修改评价状态")
    public AdminReviewResponse updateStatus(LoginUser operator, Long id, String status) {
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "";
        if (!ADMIN_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        requireManagedProduct(operator, review.getProductId());
        if (!normalizedStatus.equals(review.getStatus())) {
            review.setStatus(normalizedStatus);
            review.setUpdatedAt(LocalDateTime.now());
            reviewMapper.updateById(review);
        }
        ProductReviewRow row = reviewMapper.selectAdminReviewById(id);
        return toAdminResponse(row);
    }

    private void requireManagedProduct(LoginUser operator, Long productId) {
        if (!isSeller(operator)) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null || !operator.getUserId().equals(product.getSellerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private Long sellerId(LoginUser operator) {
        return isSeller(operator) ? operator.getUserId() : null;
    }

    private boolean isSeller(LoginUser operator) {
        return operator != null && "SELLER".equals(operator.getRole());
    }

    private MyReviewResponse toMyResponse(ProductReview review, OrderItem item) {
        return new MyReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getOrderId(),
                review.getOrderItemId(),
                item.getProductName(),
                item.getSkuSpecs(),
                item.getProductImage(),
                review.getRating(),
                review.getContent(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                ReviewImages.fromJson(review.getImagesJson()));
    }

    private MyReviewResponse toMyResponse(ProductReviewRow row) {
        return new MyReviewResponse(
                row.getId(),
                row.getProductId(),
                row.getOrderId(),
                row.getOrderItemId(),
                row.getProductName(),
                row.getSkuSpecs(),
                row.getProductImage(),
                row.getRating(),
                row.getContent(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                ReviewImages.fromJson(row.getImagesJson()));
    }

    private ProductReviewResponse toProductResponse(ProductReviewRow row) {
        return new ProductReviewResponse(
                row.getId(),
                row.getProductId(),
                row.getProductName(),
                row.getSkuSpecs(),
                row.getRating(),
                row.getContent(),
                row.getReviewerNickname(),
                row.getCreatedAt(),
                ReviewImages.fromJson(row.getImagesJson()));
    }

    private AdminReviewResponse toAdminResponse(ProductReviewRow row) {
        return new AdminReviewResponse(
                row.getId(),
                row.getUserId(),
                row.getUsername(),
                row.getReviewerNickname(),
                row.getProductId(),
                row.getProductName(),
                row.getSkuSpecs(),
                row.getRating(),
                row.getContent(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                ReviewImages.fromJson(row.getImagesJson()));
    }

    private <T> PageResult<T> mapPage(IPage<ProductReviewRow> source, java.util.function.Function<ProductReviewRow, T> mapper) {
        List<T> records = source.getRecords().stream().map(mapper).toList();
        return new PageResult<>(
                source.getCurrent(),
                source.getSize(),
                source.getTotal(),
                records);
    }

    private Product requirePublicProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
