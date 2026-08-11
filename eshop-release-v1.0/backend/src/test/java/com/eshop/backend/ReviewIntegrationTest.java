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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_review;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class ReviewIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reviewsEnforceOwnershipCompletionValidationPrivacyAndPaging() throws Exception {
        mockMvc.perform(post("/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(1, 5, "未登录评价")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/reviews/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/reviews/mine/order-items")
                        .param("orderItemIds", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        String suffix = String.valueOf(System.nanoTime());
        UserSession owner = registerAndLogin("review_owner_" + suffix, "评价用户");
        UserSession other = registerAndLogin("review_other_" + suffix, "其他用户");
        long productId = insertProduct("评价测试商品" + suffix);
        long orderId = insertOrder(owner.userId(), "REVIEW-" + suffix, "PAID");
        long itemId = insertOrderItem(orderId, productId, "评价测试商品" + suffix);

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 5, "订单还没有完成")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40929));

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 5, "不能评价别人的订单")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40417));

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(999999999L, 5, "订单商品不存在")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40417));

        jdbcTemplate.update("UPDATE orders SET status = 'COMPLETED' WHERE id = ?", orderId);

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 6, "评分越界")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 5, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 5, "x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithImages(
                                itemId,
                                5,
                                "图片过多",
                                List.of(
                                        "/api/uploads/one.png",
                                        "/api/uploads/two.png",
                                        "/api/uploads/three.png",
                                        "/api/uploads/four.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40033));
        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithImages(
                                itemId,
                                5,
                                "非法图片地址",
                                List.of("https://example.com/not-ours.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40034));

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJsonWithImages(
                                itemId,
                                5,
                                "  商品符合描述  ",
                                List.of(
                                        "/api/uploads/review-one.png",
                                        "/api/uploads/review-two.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderItemId").value(itemId))
                .andExpect(jsonPath("$.data.productName").value("评价测试商品" + suffix))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("商品符合描述"))
                .andExpect(jsonPath("$.data.imageUrls").isArray())
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("/api/uploads/review-one.png"))
                .andExpect(jsonPath("$.data.imageUrls[1]").value("/api/uploads/review-two.jpg"));

        mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(itemId, 4, "重复评价")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40930));

        mockMvc.perform(get("/products/{productId}/reviews", productId)
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(productId))
                .andExpect(jsonPath("$.data.records[0].productName").value("评价测试商品" + suffix))
                .andExpect(jsonPath("$.data.records[0].reviewerNickname").value("评价用户"))
                .andExpect(jsonPath("$.data.records[0].rating").value(5))
                .andExpect(jsonPath("$.data.records[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].orderItemId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].imageUrls").isArray())
                .andExpect(jsonPath("$.data.records[0].imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].imageUrls[0]").value("/api/uploads/review-one.png"));

        mockMvc.perform(get("/products/{productId}/reviews/summary", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.fiveStarCount").value(1))
                .andExpect(jsonPath("$.data.fourStarCount").value(0))
                .andExpect(jsonPath("$.data.threeStarCount").value(0))
                .andExpect(jsonPath("$.data.twoStarCount").value(0))
                .andExpect(jsonPath("$.data.oneStarCount").value(0));

        mockMvc.perform(get("/reviews/mine")
                        .param("current", "1")
                        .param("size", "10")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data.records[0].orderItemId").value(itemId))
                .andExpect(jsonPath("$.data.records[0].imageUrls.length()").value(2));
        mockMvc.perform(get("/reviews/mine")
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/reviews/mine/order-items")
                        .param("orderItemIds", itemId + ",999999999")
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(itemId));
        mockMvc.perform(get("/reviews/mine/order-items")
                        .param("orderItemIds", String.valueOf(itemId))
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        jdbcTemplate.update("UPDATE product SET status = 'OFF_SALE' WHERE id = ?", productId);
        mockMvc.perform(get("/products/{productId}/reviews", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));
        mockMvc.perform(get("/products/{productId}/reviews/summary", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));
    }

    private UserSession registerAndLogin(String username, String nickname) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", nickname))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        return new UserSession(data.path("userId").asLong(), data.path("token").asText());
    }

    private long insertProduct(String name) {
        jdbcTemplate.update(
                "INSERT INTO product (category_id, name, status) VALUES (1, ?, 'ON_SALE')",
                name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE name = ?",
                Long.class,
                name);
    }

    private long insertOrder(long userId, String orderNo, String orderStatus) {
        jdbcTemplate.update("""
                        INSERT INTO orders (
                            order_no, user_id, total_amount, status,
                            receiver_name, receiver_phone, receiver_address
                        ) VALUES (?, ?, 99.00, ?, '测试用户', '13800138000', '测试地址')
                        """,
                orderNo,
                userId,
                orderStatus);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?",
                Long.class,
                orderNo);
    }

    private long insertOrderItem(long orderId, long productId, String productName) {
        jdbcTemplate.update("""
                        INSERT INTO order_item (
                            order_id, product_id, sku_id, product_name, sku_specs,
                            price, quantity, subtotal
                        ) VALUES (?, ?, 1, ?, '{"颜色":"蓝色"}', 99.00, 1, 99.00)
                        """,
                orderId,
                productId,
                productName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM order_item WHERE order_id = ?",
                Long.class,
                orderId);
    }

    private String reviewJson(long orderItemId, int rating, String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderItemId", orderItemId,
                "rating", rating,
                "content", content));
    }

    private String reviewJsonWithImages(long orderItemId,
                                        int rating,
                                        String content,
                                        List<String> imageUrls) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderItemId", orderItemId,
                "rating", rating,
                "content", content,
                "imageUrls", imageUrls));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserSession(long userId, String token) {
    }
}
