ALTER TABLE product
    ADD COLUMN seller_id BIGINT NULL AFTER id;

CREATE INDEX idx_product_seller_status
    ON product (seller_id, status);

ALTER TABLE orders
    ADD COLUMN seller_id BIGINT NULL AFTER user_id;

CREATE INDEX idx_order_seller_status_created
    ON orders (seller_id, status, created_at);
