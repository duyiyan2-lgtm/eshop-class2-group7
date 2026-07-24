-- ============================================================
-- E-Shop 数据库初始化脚本
-- V1__init.sql：基础表结构
-- 负责人：吴松青（仅负责 C 模块订单相关表）
-- ============================================================

-- 等待组长架构出来后，再统一 user、product、cart_item、user_address 等表
-- 这里只放 C 模块（订单支付）的表

-- ------------------------------------------------------------
-- 订单主表（吴松青负责）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    order_no          VARCHAR(32)     NOT NULL UNIQUE     COMMENT '订单号',
    user_id           BIGINT          NOT NULL             COMMENT '用户 ID',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
    original_amount   DECIMAL(10, 2)  NOT NULL             COMMENT '商品总金额',
    discount_amount   DECIMAL(10, 2)  NOT NULL DEFAULT 0   COMMENT '优惠金额',
    payable_amount    DECIMAL(10, 2)  NOT NULL             COMMENT '实付金额',
    address_snapshot  TEXT            NOT NULL             COMMENT '收货地址快照 JSON',
    expire_at         DATETIME        NOT NULL             COMMENT '过期时间（C03 用）',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status_expire (status, expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

-- ------------------------------------------------------------
-- 订单明细表（吴松青负责）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_item (
    id               BIGINT          PRIMARY KEY AUTO_INCREMENT,
    order_id         BIGINT          NOT NULL             COMMENT '订单 ID',
    product_id       BIGINT          NOT NULL             COMMENT '商品 ID',
    product_name     VARCHAR(100)    NOT NULL             COMMENT '商品名（快照）',
    product_image    VARCHAR(255)                         COMMENT '商品图（快照）',
    unit_price       DECIMAL(10, 2)  NOT NULL             COMMENT '单价（快照）',
    quantity         INT             NOT NULL             COMMENT '数量',
    subtotal_amount  DECIMAL(10, 2)  NOT NULL             COMMENT '小计金额',
    INDEX idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表';

-- ------------------------------------------------------------
-- 支付记录表（吴松青负责）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_record (
    id           BIGINT          PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT          NOT NULL             COMMENT '订单 ID',
    channel      VARCHAR(20)     NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道（MOCK=模拟）',
    trade_no     VARCHAR(64)     NOT NULL UNIQUE      COMMENT '交易号（幂等防重）',
    amount       DECIMAL(10, 2)  NOT NULL             COMMENT '支付金额',
    status       VARCHAR(20)     NOT NULL             COMMENT 'SUCCESS / FAILED',
    paid_at      DATETIME                             COMMENT '支付时间',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付记录表';

-- ============================================================
-- C 模块（订单支付）表结构初始化完成
-- 其他模块的表（user、product、cart_item 等）由各负责人补充
-- ============================================================