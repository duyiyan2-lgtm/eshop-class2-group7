package com.eshop.backend;

import com.eshop.backend.address.AddressRequest;
import com.eshop.backend.auth.dto.LoginRequest;
import com.eshop.backend.auth.dto.RegisterRequest;
import com.eshop.backend.auth.dto.UserProfileRequest;
import com.eshop.backend.cart.dto.AddCartItemRequest;
import com.eshop.backend.cart.dto.UpdateCartItemRequest;
import com.eshop.backend.catalog.dto.CategoryRequest;
import com.eshop.backend.catalog.dto.ProductRequest;
import com.eshop.backend.catalog.dto.SkuRequest;
import com.eshop.backend.catalog.dto.StatusRequest;
import com.eshop.backend.catalog.dto.StockUpdateRequest;
import com.eshop.backend.order.dto.CreateOrderRequest;
import com.eshop.backend.user.dto.UserStatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_contract;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class ApiContractFreezeTest {

    private static final Set<String> FROZEN_V1_ENDPOINTS = Set.of(
            "DELETE /addresses/{id}",
            "DELETE /admin/categories/{id}",
            "DELETE /admin/products/{id}",
            "DELETE /admin/skus/{id}",
            "DELETE /cart/{itemId}",
            "GET /addresses",
            "GET /admin/categories",
            "GET /admin/logs",
            "GET /admin/orders",
            "GET /admin/orders/{id}",
            "GET /admin/orders/{id}/logs",
            "GET /admin/products",
            "GET /admin/products/{id}",
            "GET /admin/users",
            "GET /auth/me",
            "GET /cart",
            "GET /categories",
            "GET /orders",
            "GET /orders/{id}",
            "GET /orders/{id}/logs",
            "GET /products",
            "GET /products/{id}",
            "PATCH /addresses/{id}/default",
            "PATCH /admin/categories/{id}/status",
            "PATCH /admin/products/{id}/status",
            "PATCH /admin/skus/{id}/stock",
            "PATCH /admin/users/{id}/status",
            "PATCH /cart/{itemId}",
            "POST /addresses",
            "POST /admin/categories",
            "POST /admin/files/upload",
            "POST /admin/orders/{id}/cancel",
            "POST /admin/orders/{id}/ship",
            "POST /admin/products",
            "POST /admin/products/{productId}/skus",
            "POST /auth/login",
            "POST /auth/logout",
            "POST /auth/register",
            "POST /cart",
            "POST /orders",
            "POST /orders/{id}/cancel",
            "POST /orders/{id}/confirm",
            "POST /orders/{id}/pay",
            "PUT /addresses/{id}",
            "PUT /admin/categories/{id}",
            "PUT /admin/products/{id}",
            "PUT /admin/skus/{id}",
            "PUT /auth/me"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void frozenV1HttpMethodsAndPathsRemainAvailable() {
        Set<String> actual = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            Package handlerPackage = handlerMethod.getBeanType().getPackage();
            if (handlerPackage == null
                    || !handlerPackage.getName().startsWith("com.eshop.backend")
                    || !handlerMethod.getBeanType().getSimpleName().endsWith("Controller")) {
                return;
            }
            for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
                for (String pattern : mapping.getPatternValues()) {
                    actual.add(method.name() + " " + pattern);
                }
            }
        });

        Set<String> removed = new TreeSet<>(FROZEN_V1_ENDPOINTS);
        removed.removeAll(actual);
        assertTrue(actual.containsAll(FROZEN_V1_ENDPOINTS),
                () -> "API v1 contract removed or changed endpoints: " + removed);
    }

    @Test
    void frozenV1RequestJsonFieldsDoNotDrift() {
        Map<Class<?>, List<String>> expected = new LinkedHashMap<>();
        expected.put(RegisterRequest.class, List.of("username", "password", "nickname", "role"));
        expected.put(LoginRequest.class, List.of("username", "password"));
        expected.put(UserProfileRequest.class, List.of("nickname", "phone"));
        expected.put(AddressRequest.class,
                List.of("receiverName", "phone", "province", "city", "district", "detail", "isDefault"));
        expected.put(CategoryRequest.class, List.of("parentId", "name", "sortOrder", "status"));
        expected.put(ProductRequest.class,
                List.of("categoryId", "name", "subtitle", "mainImage", "detail", "status"));
        expected.put(SkuRequest.class, List.of("skuCode", "specsJson", "price", "stock", "status"));
        expected.put(StatusRequest.class, List.of("status"));
        expected.put(StockUpdateRequest.class, List.of("stock"));
        expected.put(AddCartItemRequest.class, List.of("skuId", "quantity"));
        expected.put(UpdateCartItemRequest.class, List.of("quantity", "selected"));
        expected.put(CreateOrderRequest.class, List.of("addressId", "remark"));
        expected.put(UserStatusRequest.class, List.of("status"));

        expected.forEach((type, fields) -> {
            assertTrue(type.isRecord(), () -> type.getSimpleName() + " must remain a record");
            List<String> actual = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
            assertEquals(fields, actual,
                    () -> "API v1 request fields changed for " + type.getSimpleName());
        });
    }
}
