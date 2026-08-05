package com.eshop.backend.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.address.AddressService;
import com.eshop.backend.address.UserAddress;
import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.cart.CartItem;
import com.eshop.backend.cart.CartItemMapper;
import com.eshop.backend.cart.CartService;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.entity.ProductSku;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.catalog.mapper.ProductSkuMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.order.dto.BuyNowOrderRequest;
import com.eshop.backend.order.dto.CreateOrderRequest;
import com.eshop.backend.order.dto.OrderItemResponse;
import com.eshop.backend.order.dto.OrderResponse;
import com.eshop.backend.order.dto.OrderStatusLogResponse;
import com.eshop.backend.order.dto.PaymentResponse;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELED = "CANCELED";

    private final ShopOrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final PaymentRecordMapper paymentMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;
    private final CartService cartService;
    private final AddressService addressService;
    private final OrderTimeoutProperties orderTimeoutProperties;

    @Transactional
    public OrderResponse createOrder(LoginUser user, CreateOrderRequest request) {
        UserAddress address = addressService.requireOwned(user.getUserId(), request.addressId());
        List<CartItem> cartItems = cartService.selectedItems(user.getUserId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_CART);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            ProductSku sku = skuMapper.selectById(cartItem.getSkuId());
            if (sku == null) {
                throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
            }
            Product product = productMapper.selectById(sku.getProductId());
            if (product == null || !"ON_SALE".equals(product.getStatus()) || !"ENABLED".equals(sku.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
            }
            if (skuMapper.decrementStock(sku.getId(), cartItem.getQuantity()) != 1) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(subtotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setSkuId(sku.getId());
            item.setProductName(product.getName());
            item.setSkuSpecs(sku.getSpecsJson());
            item.setProductImage(product.getMainImage());
            item.setPrice(sku.getPrice());
            item.setQuantity(cartItem.getQuantity());
            item.setSubtotal(subtotal);
            orderItems.add(item);
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo(generateNumber("E"));
        order.setUserId(user.getUserId());
        order.setTotalAmount(total);
        order.setStatus(PENDING_PAYMENT);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(String.join(" ",
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetail()));
        order.setRemark(request.remark());
        orderMapper.insert(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
        appendStatusLog(order.getId(), null, PENDING_PAYMENT, user, "创建订单");
        cartItemMapper.deleteByIds(cartItems.stream().map(CartItem::getId).toList());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse buyNow(LoginUser user, BuyNowOrderRequest request) {
        UserAddress address = addressService.requireOwned(user.getUserId(), request.addressId());
        ProductSku sku = skuMapper.selectById(request.skuId());
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || !"ON_SALE".equals(product.getStatus()) || !"ENABLED".equals(sku.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (skuMapper.decrementStock(sku.getId(), request.quantity()) != 1) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }

        BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(request.quantity()));
        ShopOrder order = new ShopOrder();
        order.setOrderNo(generateNumber("E"));
        order.setUserId(user.getUserId());
        order.setTotalAmount(subtotal);
        order.setStatus(PENDING_PAYMENT);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(String.join(" ",
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetail()));
        order.setRemark(request.remark());
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setSkuId(sku.getId());
        item.setProductName(product.getName());
        item.setSkuSpecs(sku.getSpecsJson());
        item.setProductImage(product.getMainImage());
        item.setPrice(sku.getPrice());
        item.setQuantity(request.quantity());
        item.setSubtotal(subtotal);
        orderItemMapper.insert(item);

        appendStatusLog(order.getId(), null, PENDING_PAYMENT, user, "立即购买创建订单");
        return toResponse(order);
    }

    public PageResult<OrderResponse> pageUserOrders(Long userId, long current, long size, String status) {
        validateStatusFilter(status);
        Page<ShopOrder> page = orderMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<ShopOrder>()
                        .eq(ShopOrder::getUserId, userId)
                        .eq(StringUtils.hasText(status), ShopOrder::getStatus, status)
                        .orderByDesc(ShopOrder::getCreatedAt));
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toResponse).toList());
    }

    public PageResult<OrderResponse> pageAdminOrders(
            long current, long size, String status, String orderNo) {
        validateStatusFilter(status);
        Page<ShopOrder> page = orderMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<ShopOrder>()
                        .eq(StringUtils.hasText(status), ShopOrder::getStatus, status)
                        .like(StringUtils.hasText(orderNo), ShopOrder::getOrderNo, orderNo)
                        .orderByDesc(ShopOrder::getCreatedAt));
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toResponse).toList());
    }

    public OrderResponse getUserOrder(Long userId, Long id) {
        return toResponse(requireOwnedOrder(userId, id));
    }

    public OrderResponse getAdminOrder(Long id) {
        return toResponse(requireOrder(id));
    }

    public List<OrderStatusLogResponse> getUserOrderLogs(Long userId, Long id) {
        requireOwnedOrder(userId, id);
        return listStatusLogs(id);
    }

    public List<OrderStatusLogResponse> getAdminOrderLogs(Long id) {
        requireOrder(id);
        return listStatusLogs(id);
    }

    public List<Long> findExpiredPendingOrderIds(LocalDateTime cutoff, int batchSize) {
        int safeBatchSize = Math.min(Math.max(batchSize, 1), 500);
        return orderMapper.selectList(new LambdaQueryWrapper<ShopOrder>()
                        .select(ShopOrder::getId)
                        .eq(ShopOrder::getStatus, PENDING_PAYMENT)
                        .le(ShopOrder::getCreatedAt, cutoff)
                        .orderByAsc(ShopOrder::getCreatedAt)
                        .last("LIMIT " + safeBatchSize))
                .stream()
                .map(ShopOrder::getId)
                .toList();
    }

    @Transactional
    public boolean cancelExpiredOrder(Long id, LocalDateTime cutoff) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(
                null,
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, id)
                        .eq(ShopOrder::getStatus, PENDING_PAYMENT)
                        .le(ShopOrder::getCreatedAt, cutoff)
                        .set(ShopOrder::getStatus, CANCELED)
                        .set(ShopOrder::getCanceledAt, now));
        if (updated != 1) {
            return false;
        }
        restoreStock(id);
        appendStatusLog(id, PENDING_PAYMENT, CANCELED, null, "订单支付超时自动取消");
        return true;
    }

    @Transactional
    public PaymentResponse pay(LoginUser user, Long id) {
        ShopOrder order = requireOwnedOrder(user.getUserId(), id);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paymentCutoff = now.minusMinutes(orderTimeoutProperties.safeMinutes());
        int updated = orderMapper.update(
                null,
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, id)
                        .eq(ShopOrder::getStatus, PENDING_PAYMENT)
                        .gt(ShopOrder::getCreatedAt, paymentCutoff)
                        .set(ShopOrder::getStatus, PAID)
                        .set(ShopOrder::getPaidAt, now));
        if (updated != 1) {
            if (PENDING_PAYMENT.equals(order.getStatus())
                    && !order.getCreatedAt().isAfter(paymentCutoff)) {
                throw new BusinessException(ErrorCode.ORDER_PAYMENT_EXPIRED);
            }
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(generateNumber("P"));
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(user.getUserId());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus("SUCCESS");
        payment.setPaidAt(now);
        paymentMapper.insert(payment);
        appendStatusLog(id, PENDING_PAYMENT, PAID, user, "模拟支付成功");
        return new PaymentResponse(
                payment.getPaymentNo(),
                payment.getOrderNo(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt());
    }

    @Transactional
    public OrderResponse cancel(LoginUser user, Long id) {
        ShopOrder order = requireOwnedOrder(user.getUserId(), id);
        transition(order, PENDING_PAYMENT, CANCELED, user, "用户取消订单");
        restoreStock(id);
        return toResponse(requireOrder(id));
    }

    @Transactional
    @OperationLogAction(module = "订单管理", action = "管理员取消订单")
    public OrderResponse cancelByAdmin(LoginUser admin, Long id) {
        ShopOrder order = requireOrder(id);
        transition(order, PENDING_PAYMENT, CANCELED, admin, "管理员取消待支付订单");
        restoreStock(id);
        return toResponse(requireOrder(id));
    }

    @Transactional
    @OperationLogAction(module = "订单管理", action = "订单发货")
    public OrderResponse ship(LoginUser operator, Long id) {
        ShopOrder order = requireOrder(id);
        String message = "SELLER".equals(operator.getRole()) ? "商家发货" : "管理员发货";
        transition(order, PAID, SHIPPED, operator, message);
        return toResponse(requireOrder(id));
    }

    @Transactional
    public OrderResponse confirm(LoginUser user, Long id) {
        ShopOrder order = requireOwnedOrder(user.getUserId(), id);
        transition(order, SHIPPED, COMPLETED, user, "用户确认收货");
        return toResponse(requireOrder(id));
    }

    private void transition(
            ShopOrder order,
            String expectedStatus,
            String targetStatus,
            LoginUser operator,
            String remark) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ShopOrder> update = new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getId, order.getId())
                .eq(ShopOrder::getStatus, expectedStatus)
                .set(ShopOrder::getStatus, targetStatus);
        if (CANCELED.equals(targetStatus)) {
            update.set(ShopOrder::getCanceledAt, now);
        } else if (SHIPPED.equals(targetStatus)) {
            update.set(ShopOrder::getShippedAt, now);
        } else if (COMPLETED.equals(targetStatus)) {
            update.set(ShopOrder::getCompletedAt, now);
        }
        if (orderMapper.update(null, update) != 1) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        appendStatusLog(order.getId(), expectedStatus, targetStatus, operator, remark);
    }

    private void restoreStock(Long orderId) {
        for (OrderItem item : listItems(orderId)) {
            skuMapper.incrementStock(item.getSkuId(), item.getQuantity());
        }
    }

    private ShopOrder requireOwnedOrder(Long userId, Long id) {
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getId, id)
                .eq(ShopOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private ShopOrder requireOrder(Long id) {
        ShopOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void appendStatusLog(
            Long orderId,
            String fromStatus,
            String toStatus,
            LoginUser operator,
            String remark) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        if (operator != null) {
            log.setOperatorId(operator.getUserId());
            log.setOperatorName(operator.getUsername());
        }
        log.setRemark(remark);
        statusLogMapper.insert(log);
    }

    private List<OrderItem> listItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
    }

    private List<OrderStatusLogResponse> listStatusLogs(Long orderId) {
        return statusLogMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderId, orderId)
                        .orderByAsc(OrderStatusLog::getCreatedAt)
                        .orderByAsc(OrderStatusLog::getId))
                .stream()
                .map(log -> new OrderStatusLogResponse(
                        log.getId(),
                        log.getFromStatus(),
                        log.getToStatus(),
                        log.getOperatorId(),
                        log.getOperatorName(),
                        log.getRemark(),
                        log.getCreatedAt()))
                .toList();
    }

    private OrderResponse toResponse(ShopOrder order) {
        List<OrderItemResponse> items = listItems(order.getId()).stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getSkuId(),
                        item.getProductName(),
                        item.getSkuSpecs(),
                        item.getProductImage(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getSubtotal()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getRemark(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCanceledAt(),
                order.getCreatedAt(),
                items);
    }

    private void validateStatusFilter(String status) {
        if (StringUtils.hasText(status)
                && !List.of(PENDING_PAYMENT, PAID, SHIPPED, COMPLETED, CANCELED).contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String generateNumber(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + timestamp + suffix;
    }
}
