-- 将课程演示车型封面从旧 SVG 占位改为本地写实 WebP，按现有 main_image 路径替换，不写死主键。
UPDATE product
SET main_image = REPLACE(main_image, '/su7/main.svg', '/su7/white/front45.webp')
WHERE product_kind = 'VEHICLE'
  AND main_image LIKE '%/su7/main.svg';

UPDATE product
SET main_image = REPLACE(main_image, '/yu7/main.svg', '/yu7/white/front45.webp')
WHERE product_kind = 'VEHICLE'
  AND main_image LIKE '%/yu7/main.svg';

UPDATE product
SET main_image = REPLACE(main_image, '/ultra/main.svg', '/ultra/white/front45.webp')
WHERE product_kind = 'VEHICLE'
  AND main_image LIKE '%/ultra/main.svg';
