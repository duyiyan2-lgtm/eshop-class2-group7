-- 课程演示汽车分类 / 车型 / 版本 SKU / 选装。
-- 价格均为课程演示数据，不是官网实时成交价，也不是官方授权销售。
-- 公开信息核对参考（非本库承诺）：
--   新一代 SU7：标准版/Pro/Max，公开报道约 21.99 / 24.99 / 30.39 万元
--   YU7：标准版/Pro/Max，公开报道约 25.35 / 27.99 / 32.99 万元
--   SU7 Ultra：官网展示预售价约 52.99 万元起
-- 按业务名称 / sku_code / (product, group code) / (group, value code) 幂等插入，不写死主键。
-- seller_id：若存在已启用 SELLER 则绑定，否则保持 NULL（与现有演示商品一致），绝不创建账号或明文密码。

INSERT INTO category (parent_id, name, sort_order, status)
SELECT NULL, '小米汽车（课程演示）', 5, 'ENABLED'
WHERE NOT EXISTS (
    SELECT 1 FROM category WHERE parent_id IS NULL AND name = '小米汽车（课程演示）'
);

INSERT INTO product (seller_id, category_id, name, subtitle, main_image, detail, status, product_kind)
SELECT
    (SELECT MIN(id) FROM sys_user WHERE role = 'SELLER' AND status = 'ENABLED'),
    (SELECT MIN(id) FROM category WHERE parent_id IS NULL AND name = '小米汽车（课程演示）'),
    demo.product_name,
    demo.subtitle,
    demo.main_image,
    demo.detail_text,
    'ON_SALE',
    'VEHICLE'
FROM (
    SELECT
        '新一代小米 SU7（课程演示）' AS product_name,
        '课程演示轿车 · 非官方销售' AS subtitle,
        '/product-images/vehicles/su7/main.svg' AS main_image,
        '非小米汽车官方页面，仅用于课程项目演示。\n本条为课堂选配器示例商品。SKU 价格为课程演示价，参考公开报道的新一代 SU7 版本区间，不代表官网实时售价或购车权益。\n请勿将本页当作官方选配或下订入口。' AS detail_text
    UNION ALL SELECT
        '小米 YU7（课程演示）',
        '课程演示 SUV · 非官方销售',
        '/product-images/vehicles/yu7/main.svg',
        '非小米汽车官方页面，仅用于课程项目演示。\n本条为课堂选配器示例商品。SKU 价格为课程演示价，参考公开报道的 YU7 标准版/Pro/Max 区间，不代表官网实时售价。'
    UNION ALL SELECT
        '小米 SU7 Ultra（课程演示）',
        '课程演示性能轿车 · 非官方销售',
        '/product-images/vehicles/ultra/main.svg',
        '非小米汽车官方页面，仅用于课程项目演示。\n本条为课堂选配器示例商品。SKU 价格为课程演示价，参考官网公开预售价“52.99 万起”的数量级，不代表可售库存或官方配置。'
) demo
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = demo.product_name);

INSERT INTO product_sku (product_id, sku_code, specs_json, price, stock, status)
SELECT
    (SELECT MIN(id) FROM product WHERE name = demo.product_name),
    demo.sku_code,
    demo.specs_json,
    demo.price,
    demo.stock,
    'ENABLED'
FROM (
    SELECT '新一代小米 SU7（课程演示）' product_name, 'COURSE-XMEV-SU7-STD' sku_code,
           '{"车型版本":"标准版","驱动形式":"后驱","课程演示":"是"}' specs_json, 219900.00 price, 12 stock
    UNION ALL SELECT '新一代小米 SU7（课程演示）', 'COURSE-XMEV-SU7-PRO',
           '{"车型版本":"Pro","驱动形式":"后驱","课程演示":"是"}', 249900.00, 10
    UNION ALL SELECT '新一代小米 SU7（课程演示）', 'COURSE-XMEV-SU7-MAX',
           '{"车型版本":"Max","驱动形式":"四驱","课程演示":"是"}', 303900.00, 6
    UNION ALL SELECT '小米 YU7（课程演示）', 'COURSE-XMEV-YU7-STD',
           '{"车型版本":"标准版","驱动形式":"后驱","课程演示":"是"}', 253500.00, 10
    UNION ALL SELECT '小米 YU7（课程演示）', 'COURSE-XMEV-YU7-PRO',
           '{"车型版本":"Pro","驱动形式":"四驱","课程演示":"是"}', 279900.00, 8
    UNION ALL SELECT '小米 YU7（课程演示）', 'COURSE-XMEV-YU7-MAX',
           '{"车型版本":"Max","驱动形式":"四驱","课程演示":"是"}', 329900.00, 5
    UNION ALL SELECT '小米 SU7 Ultra（课程演示）', 'COURSE-XMEV-ULTRA-BASE',
           '{"车型版本":"Ultra","驱动形式":"四驱","课程演示":"是"}', 529900.00, 3
) demo
WHERE NOT EXISTS (SELECT 1 FROM product_sku WHERE sku_code = demo.sku_code);

INSERT INTO vehicle_option_group (product_id, code, name, selection_type, required, sort_order, status)
SELECT p.id, g.code, g.name, g.selection_type, g.required, g.sort_order, 'ENABLED'
FROM product p
JOIN (
    SELECT 'COLOR' code, '外观颜色' name, 'SINGLE' selection_type, 1 required, 10 sort_order
    UNION ALL SELECT 'WHEEL', '轮毂', 'SINGLE', 1, 20
    UNION ALL SELECT 'INTERIOR', '内饰', 'SINGLE', 1, 30
    UNION ALL SELECT 'PACK', '选装包', 'MULTI', 0, 40
) g
WHERE p.name IN (
    '新一代小米 SU7（课程演示）',
    '小米 YU7（课程演示）',
    '小米 SU7 Ultra（课程演示）'
)
AND NOT EXISTS (
    SELECT 1 FROM vehicle_option_group existing
    WHERE existing.product_id = p.id AND existing.code = g.code
);

INSERT INTO vehicle_option_value (group_id, code, name, price_delta, preview_image, color_hex, sort_order, status)
SELECT grp.id, val.code, val.name, val.price_delta, val.preview_image, val.color_hex, val.sort_order, 'ENABLED'
FROM vehicle_option_group grp
JOIN product p ON p.id = grp.product_id
JOIN (
    SELECT 'COLOR' group_code, 'WHITE' code, '演示白' name, 0.00 price_delta, NULL preview_image, '#F4F1EA' color_hex, 10 sort_order
    UNION ALL SELECT 'COLOR', 'BLACK', '演示黑', 0.00, NULL, '#1F2328', 20
    UNION ALL SELECT 'COLOR', 'SILVER', '演示银', 0.00, NULL, '#B8C0C8', 30
    UNION ALL SELECT 'COLOR', 'GREEN', '演示绿', 8000.00, NULL, '#5E7A62', 40
    UNION ALL SELECT 'WHEEL', 'W19', '19英寸演示轮毂', 0.00, NULL, NULL, 10
    UNION ALL SELECT 'WHEEL', 'W20', '20英寸演示轮毂', 8000.00, NULL, NULL, 20
    UNION ALL SELECT 'WHEEL', 'W21', '21英寸演示轮毂', 15000.00, NULL, NULL, 30
    UNION ALL SELECT 'INTERIOR', 'BLACK', '深色织呢演示内饰', 0.00, NULL, '#2A2A2A', 10
    UNION ALL SELECT 'INTERIOR', 'BEIGE', '浅色皮革演示内饰', 6000.00, NULL, '#D8C7B0', 20
    UNION ALL SELECT 'INTERIOR', 'SPORT', '运动双色演示内饰', 8000.00, NULL, '#7A1F1F', 30
    UNION ALL SELECT 'PACK', 'WINTER', '冬季套装（课程演示）', 3500.00, NULL, NULL, 10
    UNION ALL SELECT 'PACK', 'COMFORT', '舒适套装（课程演示）', 8000.00, NULL, NULL, 20
    UNION ALL SELECT 'PACK', 'TRACK', '赛道套装（课程演示）', 20000.00, NULL, NULL, 30
) val ON val.group_code = grp.code
WHERE p.name IN (
    '新一代小米 SU7（课程演示）',
    '小米 YU7（课程演示）',
    '小米 SU7 Ultra（课程演示）'
)
AND NOT EXISTS (
    SELECT 1 FROM vehicle_option_value existing
    WHERE existing.group_id = grp.id AND existing.code = val.code
);

-- 标准版不提供 21 寸轮毂与赛道套装。
INSERT INTO vehicle_sku_option_rule (sku_id, option_value_id, available, included, price_override)
SELECT sku.id, val.id, 0, 0, NULL
FROM product_sku sku
JOIN product p ON p.id = sku.product_id
JOIN vehicle_option_group grp ON grp.product_id = p.id
JOIN vehicle_option_value val ON val.group_id = grp.id
WHERE sku.sku_code IN ('COURSE-XMEV-SU7-STD', 'COURSE-XMEV-YU7-STD')
  AND (
      (grp.code = 'WHEEL' AND val.code = 'W21')
      OR (grp.code = 'PACK' AND val.code = 'TRACK')
  )
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_sku_option_rule existing
      WHERE existing.sku_id = sku.id AND existing.option_value_id = val.id
  );

-- Ultra 将 21 寸轮毂视为版本附带（不加价），赛道套装可单独加购。
INSERT INTO vehicle_sku_option_rule (sku_id, option_value_id, available, included, price_override)
SELECT sku.id, val.id, 1, 1, 0.00
FROM product_sku sku
JOIN product p ON p.id = sku.product_id
JOIN vehicle_option_group grp ON grp.product_id = p.id
JOIN vehicle_option_value val ON val.group_id = grp.id
WHERE sku.sku_code = 'COURSE-XMEV-ULTRA-BASE'
  AND grp.code = 'WHEEL'
  AND val.code = 'W21'
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_sku_option_rule existing
      WHERE existing.sku_id = sku.id AND existing.option_value_id = val.id
  );
