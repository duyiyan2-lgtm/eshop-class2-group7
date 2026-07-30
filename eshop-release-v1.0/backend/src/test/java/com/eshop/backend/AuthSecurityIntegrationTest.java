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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectedEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(get("/admin/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/files/upload"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void utf8PasswordBeyondBcryptByteLimitReturnsValidationError() throws Exception {
        String oversizedUtf8Password = "密".repeat(30);
        String username = "utf8_" + System.nanoTime();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", oversizedUtf8Password,
                                "nickname", "UTF8密码测试"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", oversizedUtf8Password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void adminAndUserPermissionsAreEnforced() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123", "ADMIN");

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/admin/logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String username = "user_" + System.nanoTime();
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "test123456",
                                  "nickname": "测试用户"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String userToken = loginAndGetToken(username, "test123456", "USER");
        mockMvc.perform(get("/admin/logs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void completeCommerceFlowWorksFromCatalogToReceipt() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = loginAndGetToken("admin", "admin123", "ADMIN");

        long categoryId = dataId(mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "自动化分类" + suffix,
                                "sortOrder", 1,
                                "status", "ENABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("自动化分类" + suffix))
                .andReturn());

        long productId = dataId(mockMvc.perform(post("/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", categoryId,
                                "name", "自动化商品" + suffix,
                                "subtitle", "交易流程测试商品",
                                "detail", "商品详情",
                                "status", "DRAFT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn());

        long skuId = dataId(mockMvc.perform(post("/admin/products/{id}/skus", productId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuCode", "SKU-" + suffix,
                                "specsJson", "{\"颜色\":\"黑色\"}",
                                "price", 99.90,
                                "stock", 10,
                                "status", "ENABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(10))
                .andReturn());

        mockMvc.perform(patch("/admin/products/{id}/status", productId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].id").value(skuId));

        String username = "buyer_" + suffix;
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test123456",
                                "nickname", "交易测试用户"))))
                .andExpect(status().isOk());
        String userToken = loginAndGetToken(username, "test123456", "USER");

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2));

        long addressId = dataId(mockMvc.perform(post("/addresses")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", "张三",
                                "phone", "13800138000",
                                "province", "广东省",
                                "city", "广州市",
                                "district", "天河区",
                                "detail", "测试路 1 号",
                                "isDefault", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn());

        long orderId = dataId(mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId,
                                "remark", "请尽快发货"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(199.80))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andReturn());

        mockMvc.perform(post("/orders/{id}/pay", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(post("/admin/orders/{id}/ship", orderId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        mockMvc.perform(post("/orders/{id}/confirm", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/admin/products/{id}", productId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(8));

        MvcResult usersResult = mockMvc.perform(get("/admin/users")
                        .param("keyword", username)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        long userId = objectMapper.readTree(usersResult.getResponse().getContentAsString())
                .path("data").path("records").path(0).path("id").asLong();

        mockMvc.perform(patch("/admin/users/{id}/status", userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test123456"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        mockMvc.perform(get("/admin/logs")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String loginAndGetToken(String username, String password, String expectedRole) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(expectedRole))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }
}
