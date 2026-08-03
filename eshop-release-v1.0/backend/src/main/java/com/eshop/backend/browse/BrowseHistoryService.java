package com.eshop.backend.browse;

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
public class BrowseHistoryService {
    private final ProductBrowseHistoryMapper browseHistoryMapper;
    private final ProductMapper productMapper;

    @Transactional
    public void record(Long userId, Long productId) {
        requireOnSaleProduct(productId);
        LocalDateTime now = LocalDateTime.now();
        ProductBrowseHistory existing = browseHistoryMapper.selectOne(new LambdaQueryWrapper<ProductBrowseHistory>()
                .eq(ProductBrowseHistory::getUserId, userId)
                .eq(ProductBrowseHistory::getProductId, productId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setBrowsedAt(now);
            browseHistoryMapper.updateById(existing);
            return;
        }

        ProductBrowseHistory history = new ProductBrowseHistory();
        history.setUserId(userId);
        history.setProductId(productId);
        history.setBrowsedAt(now);
        try {
            browseHistoryMapper.insert(history);
        } catch (DuplicateKeyException exception) {
            ProductBrowseHistory concurrent = browseHistoryMapper.selectOne(new LambdaQueryWrapper<ProductBrowseHistory>()
                    .eq(ProductBrowseHistory::getUserId, userId)
                    .eq(ProductBrowseHistory::getProductId, productId)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                concurrent.setBrowsedAt(now);
                browseHistoryMapper.updateById(concurrent);
            } else {
                log.debug("并发写入浏览历史后未找到记录 userId={}, productId={}", userId, productId);
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResult<BrowseHistoryProductResponse> page(Long userId, long current, long size) {
        Page<BrowseHistoryProductResponse> page = new Page<>(
                Math.max(current, 1),
                Math.min(Math.max(size, 1), 50));
        return PageResult.from(browseHistoryMapper.selectBrowsePage(page, userId));
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        requirePositiveProductId(productId);
        browseHistoryMapper.delete(new LambdaQueryWrapper<ProductBrowseHistory>()
                .eq(ProductBrowseHistory::getUserId, userId)
                .eq(ProductBrowseHistory::getProductId, productId));
    }

    @Transactional
    public void clear(Long userId) {
        browseHistoryMapper.delete(new LambdaQueryWrapper<ProductBrowseHistory>()
                .eq(ProductBrowseHistory::getUserId, userId));
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
