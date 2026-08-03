package com.eshop.backend.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final ProductFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    @Transactional
    public void add(Long userId, Long productId) {
        requireOnSaleProduct(productId);
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId));
        if (count != null && count > 0) {
            return;
        }

        ProductFavorite favorite = new ProductFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        favorite.setCreatedAt(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException exception) {
            log.debug("并发收藏已由唯一索引处理，userId={}, productId={}", userId, productId);
        }
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        requirePositiveProductId(productId);
        favoriteMapper.delete(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId));
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(Long userId, Long productId) {
        requirePositiveProductId(productId);
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId)
                .eq(ProductFavorite::getProductId, productId));
        return new FavoriteStatusResponse(productId, count != null && count > 0);
    }

    @Transactional(readOnly = true)
    public PageResult<FavoriteProductResponse> page(Long userId, long current, long size) {
        Page<FavoriteProductResponse> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 50));
        return PageResult.from(favoriteMapper.selectFavoritePage(page, userId));
    }

    private void requireOnSaleProduct(Long productId) {
        requirePositiveProductId(productId);
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!"ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
    }

    private void requirePositiveProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
