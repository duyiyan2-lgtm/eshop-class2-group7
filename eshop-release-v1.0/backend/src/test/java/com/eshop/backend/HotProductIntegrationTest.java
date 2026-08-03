package com.eshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_hot_products;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
        "app.bootstrap-admin.nickname=测试管理员",
        "app.order-timeout.enabled=false"
})
@AutoConfigureMockMvc
class HotProductIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rankingCountsOnlyRecentEffectiveOrdersAndOnSaleProducts() throws Exception {
        long firstProduct = insertProduct("热销第一名", "ON_SALE", "HOT-FIRST", 49, new BigDecimal("10.00"));
        long secondProduct = insertProduct("热销第二名", "ON_SALE", "HOT-SECOND", 19, new BigDecimal("100.00"));
        long offSaleProduct = insertProduct("已下架热销商品", "OFF_SALE", "HOT-OFF", 99, new BigDecimal("1.00"));

        insertOrderItem("HOT-COMPLETED", "COMPLETED", firstProduct, "热销第一名", 5, new BigDecimal("50.00"), LocalDateTime.now());
        insertOrderItem("HOT-PAID", "PAID", secondProduct, "热销第二名", 2, new BigDecimal("200.00"), LocalDateTime.now());
        insertOrderItem("HOT-PENDING", "PENDING_PAYMENT", firstProduct, "热销第一名", 100, new BigDecimal("1000.00"), LocalDateTime.now());
        insertOrderItem("HOT-CANCELED", "CANCELED", secondProduct, "热销第二名", 100, new BigDecimal("10000.00"), LocalDateTime.now());
        insertOrderItem("HOT-OFF-SALE", "COMPLETED", offSaleProduct, "已下架热销商品", 20, new BigDecimal("20.00"), LocalDateTime.now());
        insertOrderItem("HOT-OLD", "COMPLETED", secondProduct, "热销第二名", 50, new BigDecimal("5000.00"), LocalDateTime.now().minusDays(60));

        mockMvc.perform(get("/products/hot-ranking")
                        .param("days", "30")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value(firstProduct))
                .andExpect(jsonPath("$.data[0].salesQuantity").value(5))
                .andExpect(jsonPath("$.data[0].salesAmount").value(50.0))
                .andExpect(jsonPath("$.data[0].minPrice").value(10.0))
                .andExpect(jsonPath("$.data[0].totalStock").value(49))
                .andExpect(jsonPath("$.data[1].productId").value(secondProduct))
                .andExpect(jsonPath("$.data[1].salesQuantity").value(2));

        mockMvc.perform(get("/products/hot-ranking")
                        .param("days", "0")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(secondProduct))
                .andExpect(jsonPath("$.data[0].salesQuantity").value(52));
    }

    private long insertProduct(String name, String status, String skuCode, int stock, BigDecimal price) {
        jdbcTemplate.update(
                "INSERT INTO product (category_id, name, subtitle, status) VALUES (1, ?, '热销测试', ?)",
                name,
                status);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                name);
        jdbcTemplate.update("""
                INSERT INTO product_sku (product_id, sku_code, specs_json, price, stock, status)
                VALUES (?, ?, '{}', ?, ?, 'ENABLED')
                """, productId, skuCode, price, stock);
        return productId;
    }

    private void insertOrderItem(
            String orderNo,
            String status,
            long productId,
            String productName,
            int quantity,
            BigDecimal subtotal,
            LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    order_no, user_id, total_amount, status,
                    receiver_name, receiver_phone, receiver_address, created_at, updated_at
                ) VALUES (?, 1, ?, ?, '测试用户', '13800138000', '测试地址', ?, ?)
                """, orderNo, subtotal, status, createdAt, createdAt);
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?",
                Long.class,
                orderNo);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_sku WHERE product_id = ?",
                Long.class,
                productId);
        BigDecimal price = subtotal.divide(BigDecimal.valueOf(quantity));
        jdbcTemplate.update("""
                INSERT INTO order_item (
                    order_id, product_id, sku_id, product_name, sku_specs,
                    price, quantity, subtotal, created_at
                ) VALUES (?, ?, ?, ?, '{}', ?, ?, ?, ?)
                """, orderId, productId, skuId, productName, price, quantity, subtotal, createdAt);
    }
}
