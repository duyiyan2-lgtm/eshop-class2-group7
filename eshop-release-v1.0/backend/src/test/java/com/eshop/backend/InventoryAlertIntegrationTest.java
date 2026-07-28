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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class InventoryAlertIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void inventoryAlertsRequireAdminAndFilterActiveLowStockSkus() throws Exception {
        mockMvc.perform(get("/admin/inventory/alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String username = "inventory_" + System.nanoTime();
        register(username);
        String userToken = login(username, "password123");
        mockMvc.perform(get("/admin/inventory/alerts")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        insertFixtures();
        String adminToken = login("admin", "admin123");

        mockMvc.perform(get("/admin/inventory/alerts")
                        .header("Authorization", bearer(adminToken))
                        .param("current", "1")
                        .param("size", "20")
                        .param("threshold", "10")
                        .param("keyword", "预警测试手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].skuCode").value("ALERT-ZERO"))
                .andExpect(jsonPath("$.data.records[0].stock").value(0))
                .andExpect(jsonPath("$.data.records[1].skuCode").value("ALERT-FIVE"))
                .andExpect(jsonPath("$.data.records[1].stock").value(5));

        mockMvc.perform(get("/admin/inventory/alerts")
                        .header("Authorization", bearer(adminToken))
                        .param("threshold", "0")
                        .param("keyword", "ALERT-ZERO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.data.records[0].skuStatus").value("ENABLED"));

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockSkuCount").value(3));
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", "库存测试用户"))))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.path("data").path("token").asText();
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                INSERT INTO product (category_id, name, status)
                VALUES
                    (1, '库存预警测试手机', 'ON_SALE'),
                    (1, '库存预警已下架商品', 'OFF_SALE'),
                    (1, '其他低库存商品', 'ON_SALE')
                """);
        Long phoneId = productId("库存预警测试手机");
        Long offSaleId = productId("库存预警已下架商品");
        Long otherId = productId("其他低库存商品");
        insertSku(phoneId, "ALERT-ZERO", 0, "ENABLED");
        insertSku(phoneId, "ALERT-FIVE", 5, "ENABLED");
        insertSku(phoneId, "ALERT-ELEVEN", 11, "ENABLED");
        insertSku(phoneId, "ALERT-DISABLED", 1, "DISABLED");
        insertSku(offSaleId, "ALERT-OFF-SALE", 1, "ENABLED");
        insertSku(otherId, "ALERT-OTHER", 2, "ENABLED");
    }

    private Long productId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                name);
    }

    private void insertSku(Long productId, String skuCode, int stock, String status) {
        jdbcTemplate.update("""
                INSERT INTO product_sku (
                    product_id, sku_code, specs_json, price, stock, status
                ) VALUES (?, ?, '{}', 99.00, ?, ?)
                """,
                productId,
                skuCode,
                stock,
                status);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
