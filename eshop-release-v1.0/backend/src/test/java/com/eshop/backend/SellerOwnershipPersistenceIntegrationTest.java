package com.eshop.backend;

import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.order.ShopOrder;
import com.eshop.backend.order.ShopOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_seller_ownership;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "jwt.secret=ZXNob3AtdGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC0yMDI2",
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.username=admin",
        "app.bootstrap-admin.password=admin123",
        "app.bootstrap-admin.nickname=平台管理员",
        "app.order-timeout.enabled=false"
})
class SellerOwnershipPersistenceIntegrationTest {

    private static final long SELLER_ID = 20001L;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Test
    void sellerOwnershipAndLegacyNullOwnershipCanBothBePersisted() {
        Product ownedProduct = product("商家归属测试商品");
        ownedProduct.setSellerId(SELLER_ID);
        productMapper.insert(ownedProduct);

        Product legacyProduct = product("平台历史测试商品");
        productMapper.insert(legacyProduct);

        assertEquals(SELLER_ID, productMapper.selectById(ownedProduct.getId()).getSellerId());
        assertNull(productMapper.selectById(legacyProduct.getId()).getSellerId());

        ShopOrder ownedOrder = order("SELLER-ORDER-0001");
        ownedOrder.setSellerId(SELLER_ID);
        orderMapper.insert(ownedOrder);

        ShopOrder legacyOrder = order("LEGACY-ORDER-0001");
        orderMapper.insert(legacyOrder);

        assertEquals(SELLER_ID, orderMapper.selectById(ownedOrder.getId()).getSellerId());
        assertNull(orderMapper.selectById(legacyOrder.getId()).getSellerId());
    }

    private Product product(String name) {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName(name);
        product.setStatus("DRAFT");
        return product;
    }

    private ShopOrder order(String orderNo) {
        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setUserId(10001L);
        order.setTotalAmount(new BigDecimal("19.90"));
        order.setStatus("PENDING_PAYMENT");
        order.setReceiverName("测试收货人");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("测试省 测试市 测试区 测试地址");
        return order;
    }
}
