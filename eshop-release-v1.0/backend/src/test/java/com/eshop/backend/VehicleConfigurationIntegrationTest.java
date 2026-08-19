package com.eshop.backend;

import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.vehicle.VehicleConfigurationService;
import com.eshop.backend.vehicle.dto.VehicleQuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eshop_vehicle;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class VehicleConfigurationIntegrationTest {

    @Autowired
    private VehicleConfigurationService configurationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void quoteAddsOptionDeltaAndRejectsIllegalCombinations() {
        long categoryId = insert("INSERT INTO category(name, sort_order, status) VALUES ('课程汽车', 1, 'ENABLED')");
        long productId = insert("""
                INSERT INTO product(category_id, name, subtitle, detail, status, product_kind)
                VALUES (?, '演示车型', '课程演示', '非官方演示', 'ON_SALE', 'VEHICLE')
                """, categoryId);
        long skuId = insert("""
                INSERT INTO product_sku(product_id, sku_code, specs_json, price, stock, status)
                VALUES (?, 'COURSE-XMEV-TEST-PRO', '{"车型版本":"Pro","驱动形式":"后驱","课程演示":"是"}', 199900.00, 5, 'ENABLED')
                """, productId);
        long colorGroup = insert("""
                INSERT INTO vehicle_option_group(product_id, code, name, selection_type, required, sort_order, status)
                VALUES (?, 'COLOR', '外观颜色', 'SINGLE', TRUE, 1, 'ENABLED')
                """, productId);
        long wheelGroup = insert("""
                INSERT INTO vehicle_option_group(product_id, code, name, selection_type, required, sort_order, status)
                VALUES (?, 'WHEEL', '轮毂', 'SINGLE', TRUE, 2, 'ENABLED')
                """, productId);
        long white = insert("""
                INSERT INTO vehicle_option_value(group_id, code, name, price_delta, color_hex, sort_order, status)
                VALUES (?, 'WHITE', '海湾白', 0.00, '#F5F5F0', 1, 'ENABLED')
                """, colorGroup);
        long green = insert("""
                INSERT INTO vehicle_option_value(group_id, code, name, price_delta, color_hex, sort_order, status)
                VALUES (?, 'GREEN', '霞光绿', 10000.00, '#6B8F71', 2, 'ENABLED')
                """, colorGroup);
        long wheel = insert("""
                INSERT INTO vehicle_option_value(group_id, code, name, price_delta, sort_order, status)
                VALUES (?, 'W19', '19英寸轮毂', 0.00, 1, 'ENABLED')
                """, wheelGroup);
        long locked = insert("""
                INSERT INTO vehicle_option_value(group_id, code, name, price_delta, sort_order, status)
                VALUES (?, 'W21', '21英寸轮毂', 8000.00, 2, 'ENABLED')
                """, wheelGroup);
        jdbcTemplate.update("""
                INSERT INTO vehicle_sku_option_rule(sku_id, option_value_id, available, included, price_override)
                VALUES (?, ?, FALSE, FALSE, NULL)
                """, skuId, locked);

        VehicleQuoteResponse quote = configurationService.quote(productId, skuId, List.of(green, wheel, wheel));
        assertEquals(new BigDecimal("199900.00"), quote.basePrice());
        assertEquals(new BigDecimal("10000.00"), quote.optionAmount());
        assertEquals(new BigDecimal("209900.00"), quote.unitPrice());
        assertTrue(quote.configurationSummary().contains("霞光绿"));
        assertEquals(quote.configurationHash(), configurationService.quote(productId, skuId, List.of(wheel, green)).configurationHash());

        BusinessException missingColor = assertThrows(
                BusinessException.class,
                () -> configurationService.quote(productId, skuId, List.of(wheel)));
        assertEquals(ErrorCode.VEHICLE_OPTION_REQUIRED.code(), missingColor.getCode());

        BusinessException twoColors = assertThrows(
                BusinessException.class,
                () -> configurationService.quote(productId, skuId, List.of(white, green, wheel)));
        assertEquals(ErrorCode.VEHICLE_OPTION_GROUP_INVALID.code(), twoColors.getCode());

        BusinessException incompatible = assertThrows(
                BusinessException.class,
                () -> configurationService.quote(productId, skuId, List.of(green, locked)));
        assertEquals(ErrorCode.VEHICLE_OPTION_NOT_COMPATIBLE.code(), incompatible.getCode());

        BusinessException notVehicle = assertThrows(
                BusinessException.class,
                () -> configurationService.quote(productId + 99, skuId, List.of(green, wheel)));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND.code(), notVehicle.getCode());
    }

    private long insert(String sql, Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }
}
