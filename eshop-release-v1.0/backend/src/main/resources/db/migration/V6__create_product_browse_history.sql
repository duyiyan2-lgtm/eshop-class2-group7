-- 商品浏览历史：同一用户同一商品仅保留一条记录（更新浏览时间）
CREATE TABLE IF NOT EXISTS product_browse_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    browsed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_browse_user_product (user_id, product_id),
    INDEX idx_browse_user_browsed (user_id, browsed_at),
    INDEX idx_browse_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
