package com.eshop.backend.cart;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.backend.cart.dto.AddCartItemRequest;
import com.eshop.backend.cart.dto.CartItemResponse;
import com.eshop.backend.cart.dto.UpdateCartItemRequest;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.entity.ProductSku;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.catalog.mapper.ProductSkuMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.vehicle.VehicleConfigurationHasher;
import com.eshop.backend.vehicle.VehicleConfigurationService;
import com.eshop.backend.vehicle.dto.VehicleQuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final VehicleConfigurationService configurationService;

    public List<CartItemResponse> list(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getUpdatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CartItemResponse add(Long userId, AddCartItemRequest request) {
        ProductSku sku = requirePurchasableSku(request.skuId());
        Product product = productMapper.selectById(sku.getProductId());
        VehicleQuoteResponse quote = null;
        if (configurationService.isVehicle(product) || (request.optionValueIds() != null && !request.optionValueIds().isEmpty())) {
            quote = configurationService.quote(sku.getProductId(), sku.getId(), request.optionValueIds());
            if (!quote.purchasable()) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }
        }
        String configurationHash = quote == null ? VehicleConfigurationHasher.EMPTY_HASH : quote.configurationHash();
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, request.skuId())
                .eq(CartItem::getConfigurationHash, configurationHash));
        int targetQuantity = request.quantity();
        if (item == null) {
            item = new CartItem();
            item.setUserId(userId);
            item.setSkuId(request.skuId());
            item.setQuantity(targetQuantity);
            item.setSelected(true);
            item.setConfigurationHash(configurationHash);
            item.setOptionAmount(quote == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : quote.optionAmount());
            if (quote != null) {
                item.setConfigurationJson(quote.configurationJson());
                item.setConfigurationSummary(quote.configurationSummary());
            }
        } else {
            targetQuantity += item.getQuantity();
            item.setQuantity(targetQuantity);
            item.setSelected(true);
        }
        validateQuantity(targetQuantity, sku);
        if (item.getId() == null) {
            cartItemMapper.insert(item);
        } else {
            cartItemMapper.updateById(item);
        }
        return toResponse(item);
    }

    @Transactional
    public CartItemResponse update(Long userId, Long itemId, UpdateCartItemRequest request) {
        CartItem item = requireOwnedItem(userId, itemId);
        if (request.quantity() == null && request.selected() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (request.quantity() != null) {
            ProductSku sku = requirePurchasableSku(item.getSkuId());
            validateQuantity(request.quantity(), sku);
            item.setQuantity(request.quantity());
        }
        if (request.selected() != null) {
            item.setSelected(request.selected());
        }
        cartItemMapper.updateById(item);
        return toResponse(item);
    }

    @Transactional
    public void remove(Long userId, Long itemId) {
        CartItem item = requireOwnedItem(userId, itemId);
        cartItemMapper.deleteById(item.getId());
    }

    public List<CartItem> selectedItems(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, true)
                .orderByAsc(CartItem::getId));
    }

    private CartItem requireOwnedItem(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    private ProductSku requirePurchasableSku(Long skuId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !"ON_SALE".equals(product.getStatus()) || !"ENABLED".equals(sku.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        return sku;
    }

    private void validateQuantity(int quantity, ProductSku sku) {
        if (quantity > 99) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_LIMIT);
        }
        if (quantity > sku.getStock()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
    }

    private CartItemResponse toResponse(CartItem item) {
        ProductSku sku = skuMapper.selectById(item.getSkuId());
        Product product = sku == null ? null : productMapper.selectById(sku.getProductId());
        boolean available = sku != null
                && product != null
                && "ENABLED".equals(sku.getStatus())
                && "ON_SALE".equals(product.getStatus())
                && sku.getStock() >= item.getQuantity();
        BigDecimal optionAmount = item.getOptionAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : item.getOptionAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = (sku == null ? BigDecimal.ZERO : sku.getPrice()).add(optionAmount)
                .setScale(2, RoundingMode.HALF_UP);
        return new CartItemResponse(
                item.getId(),
                item.getSkuId(),
                product == null ? null : product.getId(),
                product == null ? "商品已失效" : product.getName(),
                product == null ? null : product.getMainImage(),
                sku == null ? null : sku.getSpecsJson(),
                price,
                sku == null ? 0 : sku.getStock(),
                item.getQuantity(),
                item.getSelected(),
                available,
                optionAmount,
                item.getConfigurationHash() == null ? VehicleConfigurationHasher.EMPTY_HASH : item.getConfigurationHash(),
                item.getConfigurationSummary(),
                price.multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
