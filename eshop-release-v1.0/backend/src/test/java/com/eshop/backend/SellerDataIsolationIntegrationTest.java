package com.eshop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_seller_isolation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class SellerDataIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sellersCanOnlyManageTheirOwnCommerceData() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Session admin = login("admin", "admin123", "ADMIN");
        Session sellerA = createSeller(admin.token(), "seller_a_" + suffix);
        Session sellerB = createSeller(admin.token(), "seller_b_" + suffix);

        mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(sellerA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Forbidden category " + suffix,
                                "sortOrder", 1,
                                "status", "ENABLED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        long categoryId = dataId(mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Seller isolation " + suffix,
                                "sortOrder", 1,
                                "status", "ENABLED"))))
                .andExpect(status().isOk())
                .andReturn());

        ProductFixture productA = createProduct(
                sellerA.token(), categoryId, "Seller A product " + suffix, "A-" + suffix, 4);
        ProductFixture productB = createProduct(
                sellerB.token(), categoryId, "Seller B product " + suffix, "B-" + suffix, 3);

        assertSellerProductPage(sellerA.token(), productA.productId(), productB.productId());
        assertSellerProductPage(sellerB.token(), productB.productId(), productA.productId());
        mockMvc.perform(get("/admin/products")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/admin/products/{id}", productA.productId())
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));
        mockMvc.perform(patch("/admin/products/{id}/status", productA.productId())
                        .header("Authorization", bearer(sellerB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "OFF_SALE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));
        mockMvc.perform(post("/admin/products/{id}/skus", productA.productId())
                        .header("Authorization", bearer(sellerB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skuJson("FORBIDDEN-" + suffix, 9)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));
        mockMvc.perform(patch("/admin/skus/{id}/stock", productA.skuId())
                        .header("Authorization", bearer(sellerB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40411));

        Session buyer = registerBuyer("buyer_" + suffix);
        long addressId = dataId(mockMvc.perform(post("/addresses")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "receiverName", "Test Buyer",
                                "phone", "13800138000",
                                "province", "Guangdong",
                                "city", "Guangzhou",
                                "district", "Tianhe",
                                "detail", "No. 7 Test Road",
                                "isDefault", true))))
                .andExpect(status().isOk())
                .andReturn());

        addToCart(buyer.token(), productA.skuId());
        addToCart(buyer.token(), productB.skuId());
        mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressId", addressId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40933));

        MvcResult orderResult = mockMvc.perform(post("/orders/buy-now")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "addressId", addressId,
                                "skuId", productA.skuId(),
                                "quantity", 1,
                                "remark", "seller isolation test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andReturn();
        JsonNode orderData = data(orderResult);
        long orderId = orderData.path("id").asLong();
        long orderItemId = orderData.path("items").get(0).path("id").asLong();

        mockMvc.perform(post("/orders/{id}/pay", orderId)
                        .header("Authorization", bearer(buyer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(get("/admin/orders")
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/admin/orders/{id}", orderId)
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40415));
        mockMvc.perform(get("/admin/orders/{id}/logs", orderId)
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40415));
        mockMvc.perform(post("/admin/orders/{id}/ship", orderId)
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40415));

        mockMvc.perform(get("/admin/orders")
                        .header("Authorization", bearer(sellerA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(orderId));
        mockMvc.perform(post("/admin/orders/{id}/ship", orderId)
                        .header("Authorization", bearer(sellerA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
        mockMvc.perform(post("/orders/{id}/confirm", orderId)
                        .header("Authorization", bearer(buyer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        MvcResult reviewResult = mockMvc.perform(post("/reviews")
                        .header("Authorization", bearer(buyer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "orderItemId", orderItemId,
                                "rating", 5,
                                "content", "Seller isolation review"))))
                .andExpect(status().isOk())
                .andReturn();
        long reviewId = data(reviewResult).path("id").asLong();

        mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", bearer(sellerB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(patch("/admin/reviews/{id}/status", reviewId)
                        .header("Authorization", bearer(sellerB.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/admin/reviews")
                        .header("Authorization", bearer(sellerA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(reviewId));
        mockMvc.perform(patch("/admin/reviews/{id}/status", reviewId)
                        .header("Authorization", bearer(sellerA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        assertInventoryPage(sellerA.token(), productA.productId());
        assertInventoryPage(sellerB.token(), productB.productId());
        assertDashboard(sellerA.token(), 1, 1);
        assertDashboard(sellerB.token(), 1, 0);
        assertDashboard(admin.token(), 2, 1);

        assertOperationLogsBelongTo(sellerA);
        assertOperationLogsBelongTo(sellerB);
    }

    private Session createSeller(String adminToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "seller123",
                                "nickname", username,
                                "role", "SELLER",
                                "status", "ENABLED"))))
                .andExpect(status().isOk())
                .andReturn();
        long userId = data(result).path("id").asLong();
        Session loggedIn = login(username, "seller123", "SELLER");
        assertThat(loggedIn.userId()).isEqualTo(userId);
        return loggedIn;
    }

    private Session registerBuyer(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", "buyer123456",
                                "nickname", "Isolation Buyer"))))
                .andExpect(status().isOk());
        return login(username, "buyer123456", "USER");
    }

    private ProductFixture createProduct(String token,
                                         long categoryId,
                                         String productName,
                                         String skuCode,
                                         int stock) throws Exception {
        long productId = dataId(mockMvc.perform(post("/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryId", categoryId,
                                "name", productName,
                                "subtitle", "Isolation test product",
                                "detail", "Only its owner may manage this product",
                                "status", "DRAFT"))))
                .andExpect(status().isOk())
                .andReturn());
        long skuId = dataId(mockMvc.perform(post("/admin/products/{id}/skus", productId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skuJson(skuCode, stock)))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(patch("/admin/products/{id}/status", productId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ON_SALE"))))
                .andExpect(status().isOk());
        return new ProductFixture(productId, skuId);
    }

    private void assertSellerProductPage(String token, long visibleId, long hiddenId) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/products")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        JsonNode records = data(result).path("records");
        assertThat(records.get(0).path("id").asLong()).isEqualTo(visibleId);
        assertThat(records.toString()).doesNotContain("\"id\":" + hiddenId + ",");
    }

    private void assertInventoryPage(String token, long expectedProductId) throws Exception {
        mockMvc.perform(get("/admin/inventory/alerts")
                        .header("Authorization", bearer(token))
                        .param("threshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].productId").value(expectedProductId));
    }

    private void assertDashboard(String token, long productCount, long orderCount) throws Exception {
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(productCount))
                .andExpect(jsonPath("$.data.orderCount").value(orderCount));
    }

    private void assertOperationLogsBelongTo(Session seller) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/logs")
                        .header("Authorization", bearer(seller.token()))
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = data(result).path("records");
        assertThat(records.size()).isGreaterThan(0);
        records.forEach(record -> assertThat(record.path("operatorId").asLong())
                .isEqualTo(seller.userId()));
    }

    private void addToCart(String token, long skuId) throws Exception {
        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("skuId", skuId, "quantity", 1))))
                .andExpect(status().isOk());
    }

    private Session login(String username, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(role))
                .andReturn();
        JsonNode data = data(result);
        return new Session(data.path("userId").asLong(), data.path("token").asText());
    }

    private String skuJson(String skuCode, int stock) throws Exception {
        return json(Map.of(
                "skuCode", skuCode,
                "specsJson", "{\"color\":\"black\"}",
                "price", 88.00,
                "stock", stock,
                "status", "ENABLED"));
    }

    private long dataId(MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(long userId, String token) {
    }

    private record ProductFixture(long productId, long skuId) {
    }
}
