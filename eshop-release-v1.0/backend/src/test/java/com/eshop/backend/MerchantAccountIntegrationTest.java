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
        "spring.datasource.url=jdbc:h2:mem:eshop_merchant;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
@AutoConfigureMockMvc
class MerchantAccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void platformAdminCanManageBuyersAndSellersWhileSellerUsesMerchantConsole() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123", "ADMIN");
        String buyerUsername = "buyer_" + suffix;
        String sellerUsername = "seller_" + suffix;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", buyerUsername,
                                "password", "test123456",
                                "nickname", "测试买家"))))
                .andExpect(status().isOk());
        String buyerToken = login(buyerUsername, "test123456", "USER");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", sellerUsername,
                                "password", "seller123",
                                "nickname", "测试商家",
                                "role", "SELLER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", sellerUsername, "password", "seller123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", sellerUsername,
                                "password", "seller123",
                                "nickname", "重复商家",
                                "role", "SELLER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "public_admin_" + suffix,
                                "password", "seller123",
                                "nickname", "非法管理员",
                                "role", "ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "illegal_" + suffix,
                                "password", "seller123",
                                "nickname", "非法管理员",
                                "role", "ADMIN",
                                "status", "ENABLED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/admin/users/summary")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managedAccountCount").value(2))
                .andExpect(jsonPath("$.data.buyerCount").value(1))
                .andExpect(jsonPath("$.data.sellerCount").value(1))
                .andExpect(jsonPath("$.data.enabledCount").value(1))
                .andExpect(jsonPath("$.data.disabledCount").value(1));

        MvcResult sellerPage = mockMvc.perform(get("/admin/users")
                        .param("role", "SELLER")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(sellerUsername))
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"))
                .andReturn();
        long sellerId = responseData(sellerPage).path("records").path(0).path("id").asLong();

        mockMvc.perform(patch("/admin/users/{id}/status", sellerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        String sellerToken = login(sellerUsername, "seller123", "SELLER");
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/products")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(patch("/admin/users/{id}/role", sellerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
        mockMvc.perform(get("/admin/dashboard/summary")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/admin/users/{id}/role", sellerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/admin/users/{id}/status", sellerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password, String expectedRole) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(expectedRole))
                .andReturn();
        return responseData(result).path("token").asText();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
