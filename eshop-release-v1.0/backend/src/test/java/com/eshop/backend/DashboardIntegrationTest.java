package com.eshop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_dashboard;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dashboardRequiresAdminAndReturnsConsistentSummary() throws Exception {
        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String username = "dashboard_" + System.nanoTime();
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", "统计测试用户"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String userToken = login(username, "password123");
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        insertDashboardFixtures();

        String adminToken = login("admin", "admin123");
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userCount").value(1))
                .andExpect(jsonPath("$.data.productCount").value(2))
                .andExpect(jsonPath("$.data.onSaleProductCount").value(1))
                .andExpect(jsonPath("$.data.orderCount").value(5))
                .andExpect(jsonPath("$.data.pendingPaymentOrderCount").value(1))
                .andExpect(jsonPath("$.data.paidOrderCount").value(1))
                .andExpect(jsonPath("$.data.shippedOrderCount").value(1))
                .andExpect(jsonPath("$.data.completedOrderCount").value(1))
                .andExpect(jsonPath("$.data.canceledOrderCount").value(1))
                .andExpect(jsonPath("$.data.lowStockSkuCount").value(1))
                .andExpect(jsonPath("$.data.paidSalesAmount").value(90.0))
                .andExpect(jsonPath("$.data.generatedAt").exists());

        mockMvc.perform(get("/admin/dashboard/sales-trend")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/admin/dashboard/sales-trend")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.points.length()").value(7));

        mockMvc.perform(get("/admin/dashboard/top-products")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.limit").value(5))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/admin/dashboard/sales-trend")
                        .param("days", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.path("data").path("token").asText();
    }

    private void insertDashboardFixtures() {
        jdbcTemplate.update("""
                INSERT INTO product (category_id, name, status)
                VALUES (1, '统计在售商品', 'ON_SALE'), (1, '统计草稿商品', 'DRAFT')
                """);
        Long onSaleProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = '统计在售商品'",
                Long.class);
        jdbcTemplate.update("""
                INSERT INTO product_sku (product_id, sku_code, specs_json, price, stock, status)
                VALUES (?, 'DASHBOARD-LOW-STOCK', '{}', 19.90, 4, 'ENABLED')
                """, onSaleProductId);
        insertOrder("DASH-PENDING", "PENDING_PAYMENT", new BigDecimal("10.00"));
        insertOrder("DASH-PAID", "PAID", new BigDecimal("20.00"));
        insertOrder("DASH-SHIPPED", "SHIPPED", new BigDecimal("30.00"));
        insertOrder("DASH-COMPLETED", "COMPLETED", new BigDecimal("40.00"));
        insertOrder("DASH-CANCELED", "CANCELED", new BigDecimal("50.00"));
    }

    private void insertOrder(String orderNo, String status, BigDecimal amount) {
        jdbcTemplate.update("""
                        INSERT INTO orders (
                            order_no, user_id, total_amount, status,
                            receiver_name, receiver_phone, receiver_address
                        ) VALUES (?, 1, ?, ?, '测试用户', '13800138000', '测试地址')
                        """,
                orderNo, amount, status);
    }
}
