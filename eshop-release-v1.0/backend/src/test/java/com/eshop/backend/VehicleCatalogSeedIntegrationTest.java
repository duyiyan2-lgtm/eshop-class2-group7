package com.eshop.backend;

import com.eshop.backend.catalog.CatalogService;
import com.eshop.backend.catalog.dto.ProductDetailResponse;
import com.eshop.backend.catalog.dto.ProductSummaryResponse;
import com.eshop.backend.catalog.entity.Category;
import com.eshop.backend.common.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_vehicle_seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "jwt.secret=ZXNob3AtdGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC0yMDI2",
        "app.bootstrap-admin.enabled=false",
        "app.order-timeout.enabled=false"
})
@AutoConfigureMockMvc
class VehicleCatalogSeedIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void loadCourseVehicleSeed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V13__seed_course_xiaomi_vehicles.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V13__seed_course_xiaomi_vehicles.sql"));
        }
    }

    @Test
    void courseVehicleCatalogIsIdempotentAndQueryable() throws Exception {
        Integer categoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE name = '小米汽车（课程演示）'",
                Integer.class);
        assertEquals(1, categoryCount);

        List<Category> categories = catalogService.listCategories(true);
        assertEquals(1, categories.stream().filter(item -> "小米汽车（课程演示）".equals(item.getName())).count());

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM category WHERE name = '小米汽车（课程演示）'",
                Long.class);
        PageResult<ProductSummaryResponse> page = catalogService.pageProducts(1, 12, categoryId, null, true);
        assertEquals(3, page.total());
        assertTrue(page.records().stream().allMatch(item -> "VEHICLE".equals(item.productKind())));
        assertTrue(page.records().stream().noneMatch(item ->
                item.mainImage() != null && item.mainImage().contains("xiaomiev.com")));

        List<String> names = page.records().stream().map(ProductSummaryResponse::name).toList();
        assertTrue(names.containsAll(List.of(
                "新一代小米 SU7（课程演示）",
                "小米 YU7（课程演示）",
                "小米 SU7 Ultra（课程演示）")));

        Integer skuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_sku WHERE sku_code LIKE 'COURSE-XMEV-%'",
                Integer.class);
        assertEquals(7, skuCount);

        Long su7Id = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM product WHERE name = '新一代小米 SU7（课程演示）'",
                Long.class);
        ProductDetailResponse su7 = catalogService.getProduct(su7Id, true);
        assertEquals(3, su7.skus().size());
        assertTrue(su7.detail().contains("非小米汽车官方页面"));
        assertTrue(su7.skus().stream().allMatch(sku -> sku.specsJson().contains("车型版本")));
        assertTrue(su7.skus().stream().allMatch(sku -> sku.specsJson().contains("驱动形式")));
        assertTrue(su7.skus().stream().allMatch(sku -> sku.specsJson().contains("课程演示")));

        Integer optionGroups = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vehicle_option_group g JOIN product p ON p.id = g.product_id WHERE p.id = ?",
                Integer.class,
                su7Id);
        assertEquals(4, optionGroups);

        Integer blockedRules = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM vehicle_sku_option_rule r
                JOIN product_sku s ON s.id = r.sku_id
                WHERE s.sku_code = 'COURSE-XMEV-SU7-STD' AND r.available = FALSE
                """,
                Integer.class);
        assertEquals(2, blockedRules);

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='小米汽车（课程演示）')]").exists());
        mockMvc.perform(get("/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mockMvc.perform(get("/products/{id}", su7Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productKind").value("VEHICLE"))
                .andExpect(jsonPath("$.data.skus.length()").value(3));

        Integer remoteImages = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE name LIKE '%课程演示%' AND main_image LIKE '%xiaomiev.com%'",
                Integer.class);
        assertEquals(0, remoteImages);
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM sys_user WHERE role = 'SELLER'",
                Boolean.class)));
    }
}
