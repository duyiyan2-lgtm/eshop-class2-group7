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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_admin_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class AdminReviewIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminCanFilterHideAndRestoreReviewsWithPublicSummaryUpdated() throws Exception {
        mockMvc.perform(get("/admin/reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String suffix = String.valueOf(System.nanoTime());
        UserSession owner = registerAndLogin("review_admin_" + suffix, "评价审核用户");
        mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        long productId = insertReviewFixture(owner.userId(), suffix);
        String adminToken = login("admin", "admin123").token();

        MvcResult pageResult = mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "审核商品" + suffix)
                        .param("rating", "2")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("review_admin_" + suffix))
                .andExpect(jsonPath("$.data.records[0].reviewerNickname").value("评价审核用户"))
                .andExpect(jsonPath("$.data.records[0].productName").value("审核商品" + suffix))
                .andExpect(jsonPath("$.data.records[0].rating").value(2))
                .andReturn();
        long reviewId = objectMapper.readTree(pageResult.getResponse().getContentAsByteArray())
                .path("data").path("records").get(0).path("id").asLong();

        mockMvc.perform(patch("/admin/reviews/{id}/status", reviewId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson("HIDDEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(get("/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/products/{productId}/reviews/summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(0));

        mockMvc.perform(get("/reviews/mine")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("HIDDEN"));

        mockMvc.perform(patch("/admin/reviews/{id}/status", reviewId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson("PUBLISHED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/products/{productId}/reviews/summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(2.0));

        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE module = '评价管理' AND action = '修改评价状态' AND success = TRUE",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(logCount).isEqualTo(2);
    }

    private long insertReviewFixture(long userId, String suffix) {
        jdbcTemplate.update(
                "INSERT INTO product (category_id, name, status) VALUES (1, ?, 'ON_SALE')",
                "审核商品" + suffix);
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                "审核商品" + suffix);
        jdbcTemplate.update("""
                INSERT INTO product_sku (product_id, sku_code, specs_json, price, stock, status)
                VALUES (?, ?, '{"颜色":"黑色"}', 88.00, 10, 'ENABLED')
                """, productId, "REVIEW-ADMIN-" + suffix);
        Long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_sku WHERE sku_code = ?",
                Long.class,
                "REVIEW-ADMIN-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO orders (
                    order_no, user_id, total_amount, status,
                    receiver_name, receiver_phone, receiver_address
                ) VALUES (?, ?, 88.00, 'COMPLETED', '测试用户', '13800138000', '测试地址')
                """, "REVIEW-ADMIN-" + suffix, userId);
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?",
                Long.class,
                "REVIEW-ADMIN-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO order_item (
                    order_id, product_id, sku_id, product_name, sku_specs,
                    price, quantity, subtotal
                ) VALUES (?, ?, ?, ?, '{"颜色":"黑色"}', 88.00, 1, 88.00)
                """, orderId, productId, skuId, "审核商品" + suffix);
        Long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_item WHERE order_id = ?",
                Long.class,
                orderId);
        jdbcTemplate.update("""
                INSERT INTO product_review (
                    user_id, product_id, order_id, order_item_id, rating, content, status
                ) VALUES (?, ?, ?, ?, 2, '包装需要改进，但商品可以正常使用', 'PUBLISHED')
                """, userId, productId, orderId, itemId);
        return productId;
    }

    private UserSession registerAndLogin(String username, String nickname) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", nickname))))
                .andExpect(status().isOk());
        return login(username, "password123");
    }

    private UserSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return new UserSession(data.path("userId").asLong(), data.path("token").asText());
    }

    private String statusJson(String status) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", status));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserSession(long userId, String token) {
    }
}
