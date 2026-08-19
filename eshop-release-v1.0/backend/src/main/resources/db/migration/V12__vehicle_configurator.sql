-- 课程演示：车辆选配数据模型。
-- 不写死业务主键。金额一律 DECIMAL。旧购物车行使用 configuration_hash = 'NONE' 保持兼容。

ALTER TABLE product
    ADD COLUMN product_kind VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER status;

CREATE INDEX idx_product_kind_status ON product (product_kind, status);

CREATE TABLE IF NOT EXISTS vehicle_option_group (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    selection_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    required TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_option_group_product_code (product_id, code),
    INDEX idx_vehicle_option_group_product (product_id, sort_order, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vehicle_option_value (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    price_delta DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    preview_image VARCHAR(500) NULL,
    color_hex VARCHAR(16) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_option_value_group_code (group_id, code),
    INDEX idx_vehicle_option_value_group (group_id, sort_order, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vehicle_sku_option_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    option_value_id BIGINT NOT NULL,
    available TINYINT(1) NOT NULL DEFAULT 1,
    included TINYINT(1) NOT NULL DEFAULT 0,
    price_override DECIMAL(12, 2) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vehicle_sku_option (sku_id, option_value_id),
    INDEX idx_vehicle_sku_option_value (option_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cart_item
    ADD COLUMN configuration_hash VARCHAR(80) NOT NULL DEFAULT 'NONE' AFTER selected,
    ADD COLUMN configuration_json TEXT NULL AFTER configuration_hash,
    ADD COLUMN configuration_summary VARCHAR(500) NULL AFTER configuration_json,
    ADD COLUMN option_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER configuration_summary;

ALTER TABLE cart_item
    DROP INDEX uk_cart_user_sku,
    ADD UNIQUE KEY uk_cart_user_sku_config (user_id, sku_id, configuration_hash);

CREATE INDEX idx_cart_user_hash ON cart_item (user_id, configuration_hash);

ALTER TABLE order_item
    ADD COLUMN configuration_json TEXT NULL AFTER sku_specs,
    ADD COLUMN configuration_summary VARCHAR(500) NULL AFTER configuration_json,
    ADD COLUMN option_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER configuration_summary;
