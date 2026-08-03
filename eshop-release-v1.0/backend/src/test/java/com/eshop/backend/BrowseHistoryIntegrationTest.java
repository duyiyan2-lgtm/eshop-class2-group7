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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_browse;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class BrowseHistoryIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void browseHistoryIsAuthenticatedIdempotentPagedAndUserScoped() throws Exception {
        mockMvc.perform(get("/browse-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String suffix = String.valueOf(System.nanoTime());
        String tokenA = registerAndLogin("browse_a_" + suffix);
        String tokenB = registerAndLogin("browse_b_" + suffix);
        long onSaleProductId = insertProduct("浏览在售商品" + suffix, "ON_SALE");
        long draftProductId = insertProduct("浏览草稿商品" + suffix, "DRAFT");
        insertSku(onSaleProductId, "BRW-A-" + suffix, "19.90", 5, "ENABLED");
        insertSku(onSaleProductId, "BRW-B-" + suffix, "29.90", 8, "ENABLED");

        mockMvc.perform(post("/browse-history/{productId}", draftProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        mockMvc.perform(post("/browse-history/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/browse-history/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_browse_history WHERE product_id = ?",
                Integer.class,
                onSaleProductId);
        org.junit.jupiter.api.Assertions.assertEquals(1, historyCount);

        mockMvc.perform(get("/browse-history")
                        .param("current", "1")
                        .param("size", "10")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(onSaleProductId))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(19.90))
                .andExpect(jsonPath("$.data.records[0].totalStock").value(13))
                .andExpect(jsonPath("$.data.records[0].browsedAt").exists());

        mockMvc.perform(get("/browse-history")
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(delete("/browse-history/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/browse-history")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/browse-history/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/browse-history")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/browse-history")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", username))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return data.path("token").asText();
    }

    private long insertProduct(String name, String status) {
        jdbcTemplate.update(
                "INSERT INTO product (category_id, name, status) VALUES (1, ?, ?)",
                name,
                status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                name);
    }

    private void insertSku(long productId, String skuCode, String price, int stock, String status) {
        jdbcTemplate.update("""
                        INSERT INTO product_sku (product_id, sku_code, specs_json, price, stock, status)
                        VALUES (?, ?, '{}', ?, ?, ?)
                        """,
                productId, skuCode, price, stock, status);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
