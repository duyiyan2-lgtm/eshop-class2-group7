package com.eshop.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BackendEdgeCaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.eshop.backend.order.OrderService orderService;

    @Test
    void catalogStatusRulesCannotBeBypassedThroughCreateOrUpdate() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        long categoryId = createCategory(adminToken, "边界分类" + suffix, "ENABLED");

        mockMvc.perform(post("/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(categoryId, "违规上架商品" + suffix, "ON_SALE", true)))
                .andExpect(status().isConflict());

        long productId = createProduct(adminToken, categoryId, "正常商品" + suffix, "DRAFT");
        long skuId = createSku(adminToken, productId, "EDGE-SKU-" + suffix, 20);
        setProductStatus(adminToken, productId, "ON_SALE");

        mockMvc.perform(put("/admin/products/{id}", productId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(categoryId, "修改名称后仍上架" + suffix, null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        mockMvc.perform(put("/admin/skus/{id}", skuId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuCode", "EDGE-SKU-" + suffix,
                                "specsJson", "{\"颜色\":\"黑色\"}",
                                "price", 10.00,
                                "stock", 20,
                                "status", "DISABLED"))))
                .andExpect(status().isConflict());

        long disabledCategoryId = createCategory(adminToken, "停用分类" + suffix, "DISABLED");
        mockMvc.perform(put("/admin/categories/{id}", disabledCategoryId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "停用分类改名" + suffix,
                                "sortOrder", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(delete("/admin/categories/{id}", categoryId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40922));

        mockMvc.perform(delete("/admin/products/{id}", productId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40921));
        mockMvc.perform(delete("/admin/skus/{id}", skuId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40921));

        setProductStatus(adminToken, productId, "OFF_SALE");
        mockMvc.perform(delete("/admin/products/{id}", productId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/categories/{id}", categoryId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/categories/{id}", disabledCategoryId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void categoryHierarchyCannotEnableChildrenBelowDisabledParents() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        long parentId = createCategory(adminToken, "停用父分类" + suffix, "DISABLED");

        mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", parentId,
                                "name", "违规启用子分类" + suffix,
                                "sortOrder", 1,
                                "status", "ENABLED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40924));

        long childId = dataId(mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", parentId,
                                "name", "停用子分类" + suffix,
                                "sortOrder", 1,
                                "status", "DISABLED"))))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(patch("/admin/categories/{id}/status", childId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40924));

        mockMvc.perform(patch("/admin/categories/{id}/status", parentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/admin/categories/{id}/status", childId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/admin/categories/{id}/status", parentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40925));

        mockMvc.perform(patch("/admin/categories/{id}/status", childId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/admin/categories/{id}/status", parentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/admin/categories/{id}", childId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/categories/{id}", parentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void cartLimitAddressFlagAndOrderCancellationAreConsistent() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        CatalogIds catalog = createSaleProduct(adminToken, suffix, 200);
        String userToken = registerAndLogin("cart_" + suffix);

        MvcResult cartResult = mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 60))))
                .andExpect(status().isOk())
                .andReturn();
        long cartId = dataId(cartResult);

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 60))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40023));

        mockMvc.perform(patch("/cart/{id}", cartId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());

        long addressId = createAddress(userToken, false);
        mockMvc.perform(put("/addresses/{id}", addressId)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false));

        MvcResult orderResult = mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))))
                .andExpect(status().isOk())
                .andReturn();
        long orderId = dataId(orderResult);

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(200));

        mockMvc.perform(post("/orders/{id}/pay", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40912));

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 1))))
                .andExpect(status().isOk());
        long adminCanceledOrderId = dataId(mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/admin/orders/{id}/cancel", adminCanceledOrderId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(get("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(200));
    }

    @Test
    void buyNowCreatesOnlyRequestedItemAndPreservesCart() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        CatalogIds catalog = createSaleProduct(adminToken, suffix, 10);
        String userToken = registerAndLogin("buy_now_" + suffix);

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 2))))
                .andExpect(status().isOk());
        long addressId = createAddress(userToken, true);

        MvcResult orderResult = mockMvc.perform(post("/orders/buy-now")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId,
                                "skuId", catalog.skuId(),
                                "quantity", 3,
                                "remark", "buy-now-test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(30.0))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andReturn();
        long orderId = dataId(orderResult);

        mockMvc.perform(get("/cart")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(2));
        mockMvc.perform(get("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(7));

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(get("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(10));

        mockMvc.perform(post("/orders/buy-now")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId,
                                "skuId", catalog.skuId(),
                                "quantity", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedAndInvalidUploadRequestsReturnClientErrors() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/auth/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500));

        mockMvc.perform(get("/not-existing")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(multipart("/admin/files/upload")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));

        MockMultipartFile invalid = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());
        mockMvc.perform(multipart("/admin/files/upload")
                        .file(invalid)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/admin/files/upload")
                        .file(oversized)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));
    }

    @Test
    void usersCannotReadOtherOrdersAndDisabledTokensStopWorking() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        CatalogIds catalog = createSaleProduct(adminToken, suffix, 10);
        String ownerName = "owner_" + suffix;
        String ownerToken = registerAndLogin(ownerName);
        String otherToken = registerAndLogin("other_" + suffix);

        mockMvc.perform(put("/auth/me")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "已修改昵称",
                                "phone", "13900139000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("已修改昵称"))
                .andExpect(jsonPath("$.data.phone").value("13900139000"));
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("已修改昵称"))
                .andExpect(jsonPath("$.data.phone").value("13900139000"));

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 1))))
                .andExpect(status().isOk());
        long addressId = createAddress(ownerToken, true);
        long orderId = dataId(mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40415));

        mockMvc.perform(put("/addresses/{id}", addressId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40414));

        MvcResult users = mockMvc.perform(get("/admin/users")
                        .param("keyword", ownerName)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        long ownerId = objectMapper.readTree(users.getResponse().getContentAsString())
                .path("data").path("records").path(0).path("id").asLong();
        mockMvc.perform(patch("/admin/users/{id}/status", ownerId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void expiredOrderIsCanceledOnceAndStockIsRestoredOnce() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String adminToken = login("admin", "admin123");
        CatalogIds catalog = createSaleProduct(adminToken, suffix, 10);
        String userToken = registerAndLogin("timeout_" + suffix);

        mockMvc.perform(post("/cart")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuId", catalog.skuId(),
                                "quantity", 2))))
                .andExpect(status().isOk());
        long addressId = createAddress(userToken, true);
        long orderId = dataId(mockMvc.perform(post("/orders")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addressId", addressId))))
                .andExpect(status().isOk())
                .andReturn());

        setProductStatus(adminToken, catalog.productId(), "OFF_SALE");
        mockMvc.perform(delete("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40923));
        mockMvc.perform(delete("/admin/skus/{id}", catalog.skuId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40927));

        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(31);
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?", createdAt, orderId);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        assertTrue(orderService.findExpiredPendingOrderIds(cutoff, 100).contains(orderId));
        mockMvc.perform(post("/orders/{id}/pay", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40926));
        assertTrue(orderService.cancelExpiredOrder(orderId, cutoff));
        assertFalse(orderService.cancelExpiredOrder(orderId, cutoff));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(get("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].stock").value(10));
        mockMvc.perform(get("/orders/{id}/logs", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].fromStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[1].toStatus").value("CANCELED"))
                .andExpect(jsonPath("$.data[1].remark").value("订单支付超时自动取消"));
        mockMvc.perform(post("/orders/{id}/pay", orderId)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40912));

        mockMvc.perform(delete("/admin/skus/{id}", catalog.skuId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/admin/products/{id}", catalog.productId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private CatalogIds createSaleProduct(String adminToken, String suffix, int stock) throws Exception {
        long categoryId = createCategory(adminToken, "测试分类" + suffix, "ENABLED");
        long productId = createProduct(adminToken, categoryId, "测试商品" + suffix, "DRAFT");
        long skuId = createSku(adminToken, productId, "TEST-SKU-" + suffix, stock);
        setProductStatus(adminToken, productId, "ON_SALE");
        return new CatalogIds(productId, skuId);
    }

    private long createCategory(String token, String name, String categoryStatus) throws Exception {
        return dataId(mockMvc.perform(post("/admin/categories")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "sortOrder", 1,
                                "status", categoryStatus))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private long createProduct(String token, long categoryId, String name, String productStatus) throws Exception {
        return dataId(mockMvc.perform(post("/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(categoryId, name, productStatus, true)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private long createSku(String token, long productId, String skuCode, int stock) throws Exception {
        return dataId(mockMvc.perform(post("/admin/products/{id}/skus", productId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skuCode", skuCode,
                                "specsJson", "{\"颜色\":\"黑色\"}",
                                "price", 10.00,
                                "stock", stock,
                                "status", "ENABLED"))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void setProductStatus(String token, long productId, String productStatus) throws Exception {
        mockMvc.perform(patch("/admin/products/{id}/status", productId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", productStatus))))
                .andExpect(status().isOk());
    }

    private String productJson(
            long categoryId, String name, String productStatus, boolean includeStatus) throws Exception {
        if (includeStatus) {
            return objectMapper.writeValueAsString(Map.of(
                    "categoryId", categoryId,
                    "name", name,
                    "subtitle", "边界测试",
                    "detail", "边界测试商品",
                    "status", productStatus));
        }
        return objectMapper.writeValueAsString(Map.of(
                "categoryId", categoryId,
                "name", name,
                "subtitle", "边界测试",
                "detail", "边界测试商品"));
    }

    private long createAddress(String token, boolean isDefault) throws Exception {
        return dataId(mockMvc.perform(post("/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(isDefault)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String addressJson(boolean isDefault) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "receiverName", "测试用户",
                "phone", "13800138000",
                "province", "广东省",
                "city", "广州市",
                "district", "天河区",
                "detail", "测试路 1 号",
                "isDefault", isDefault));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test123456",
                                "nickname", "边界测试用户"))))
                .andExpect(status().isOk());
        return login(username, "test123456");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private long dataId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record CatalogIds(long productId, long skuId) {
    }
}
