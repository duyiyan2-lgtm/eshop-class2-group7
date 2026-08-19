package com.eshop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_vehicle_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class VehicleConfiguratorApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long skuId;
    private Long whiteId;
    private Long greenId;
    private Long wheelId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V13__seed_course_xiaomi_vehicles.sql"));
        }
        productId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM product WHERE name = '新一代小米 SU7（课程演示）'", Long.class);
        skuId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM product_sku WHERE sku_code = 'COURSE-XMEV-SU7-PRO'", Long.class);
        whiteId = optionId("COLOR", "WHITE");
        greenId = optionId("COLOR", "GREEN");
        wheelId = optionId("WHEEL", "W19");
    }

    @Test
    void quoteCartAndOrderUseServerSideVehiclePrice() throws Exception {
        mockMvc.perform(get("/vehicles/{id}/configurator", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("新一代小米 SU7（课程演示）"))
                .andExpect(jsonPath("$.data.disclaimer").value("非小米汽车官方页面，仅用于课程项目演示"))
                .andExpect(jsonPath("$.data.skus.length()").value(3))
                .andExpect(jsonPath("$.data.groups.length()").value(4));

        mockMvc.perform(post("/vehicles/{id}/configurator/quote", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "optionValueIds", List.of(greenId, wheelId, optionId("INTERIOR", "BLACK"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basePrice").value(249900.00))
                .andExpect(jsonPath("$.data.optionAmount").value(8000.00))
                .andExpect(jsonPath("$.data.unitPrice").value(257900.00));

        mockMvc.perform(post("/vehicles/{id}/configurator/quote", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "optionValueIds", List.of(greenId, optionId("WHEEL", "W21"))))))
                .andExpect(status().isConflict());

        String token = registerAndLogin("vehuser" + System.nanoTime());
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "quantity", 1,
                                "optionValueIds", List.of(whiteId, wheelId, optionId("INTERIOR", "BLACK"))))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "quantity", 1,
                                "optionValueIds", List.of(greenId, wheelId, optionId("INTERIOR", "BLACK"))))))
                .andExpect(status().isOk());

        MvcResult cartResult = mockMvc.perform(get("/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        JsonNode cart = objectMapper.readTree(cartResult.getResponse().getContentAsString()).path("data");
        assertNotEquals(cart.get(0).path("configurationHash").asText(), cart.get(1).path("configurationHash").asText());

        mockMvc.perform(post("/cart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", skuId,
                                "quantity", 1,
                                "optionValueIds", List.of(whiteId, wheelId, optionId("INTERIOR", "BLACK"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2));

        long addressId = createAddress(token);
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].configurationSummary").isNotEmpty());

        Integer remainingCart = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE user_id = (SELECT MIN(id) FROM sys_user WHERE username LIKE 'vehuser%')",
                Integer.class);
        assertEquals(0, remainingCart);
    }

    private Long optionId(String groupCode, String valueCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT v.id FROM vehicle_option_value v
                JOIN vehicle_option_group g ON g.id = v.group_id
                WHERE g.product_id = ? AND g.code = ? AND v.code = ?
                """,
                Long.class,
                productId, groupCode, valueCode);
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Passw0rd!",
                                "nickname", "车辆测试",
                                "role", "USER"))))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private long createAddress(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", "测试收货人",
                                "phone", "13800138000",
                                "province", "广东",
                                "city", "深圳",
                                "district", "南山区",
                                "detail", "课程路 1 号",
                                "isDefault", true))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}
