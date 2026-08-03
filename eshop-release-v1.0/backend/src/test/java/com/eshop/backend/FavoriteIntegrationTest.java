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
        "spring.datasource.url=jdbc:h2:mem:eshop_favorite;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class FavoriteIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void favoritesAreAuthenticatedIdempotentPagedAndUserScoped() throws Exception {
        mockMvc.perform(get("/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String suffix = String.valueOf(System.nanoTime());
        String tokenA = registerAndLogin("favorite_a_" + suffix);
        String tokenB = registerAndLogin("favorite_b_" + suffix);
        long onSaleProductId = insertProduct("收藏在售商品" + suffix, "ON_SALE");
        long draftProductId = insertProduct("收藏草稿商品" + suffix, "DRAFT");
        insertSku(onSaleProductId, "FAV-A-" + suffix, "19.90", 3, "ENABLED");
        insertSku(onSaleProductId, "FAV-B-" + suffix, "29.90", 7, "ENABLED");
        insertSku(onSaleProductId, "FAV-C-" + suffix, "9.90", 100, "DISABLED");

        mockMvc.perform(post("/favorites/{productId}", draftProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        mockMvc.perform(post("/favorites/{productId}", 999999999L)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));

        mockMvc.perform(post("/favorites/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/favorites/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        Integer favoriteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_favorite WHERE product_id = ?",
                Integer.class,
                onSaleProductId);
        org.junit.jupiter.api.Assertions.assertEquals(1, favoriteCount);

        mockMvc.perform(get("/favorites/{productId}/status", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(onSaleProductId))
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/favorites/{productId}/status", onSaleProductId)
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));

        mockMvc.perform(get("/favorites")
                        .param("current", "1")
                        .param("size", "10")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(onSaleProductId))
                .andExpect(jsonPath("$.data.records[0].minPrice").value(19.90))
                .andExpect(jsonPath("$.data.records[0].totalStock").value(10))
                .andExpect(jsonPath("$.data.records[0].favoritedAt").exists());

        jdbcTemplate.update("UPDATE product SET status = 'OFF_SALE' WHERE id = ?", onSaleProductId);
        mockMvc.perform(get("/favorites")
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(delete("/favorites/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/favorites/{productId}", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/favorites/{productId}/status", onSaleProductId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", "收藏测试用户"))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.path("data").path("token").asText();
    }

    private long insertProduct(String name, String productStatus) {
        jdbcTemplate.update(
                "INSERT INTO product (category_id, name, status) VALUES (1, ?, ?)",
                name,
                productStatus);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                name);
    }

    private void insertSku(long productId, String skuCode, String price, int stock, String skuStatus) {
        jdbcTemplate.update("""
                        INSERT INTO product_sku (
                            product_id, sku_code, specs_json, price, stock, status
                        ) VALUES (?, ?, '{}', ?, ?, ?)
                        """,
                productId,
                skuCode,
                price,
                stock,
                skuStatus);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
