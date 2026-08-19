-- 为演示商品补齐独立商品图。图片由前端静态目录 /product-images 提供。
-- 仅更新初始演示商品，避免覆盖组员后续创建的真实商品图片。
UPDATE product
SET main_image = CASE id
    WHEN 1 THEN '/product-images/product-01.jpg'
    WHEN 2 THEN '/product-images/product-02.jpg'
    WHEN 3 THEN '/product-images/product-03.jpg'
    WHEN 4 THEN '/product-images/product-04.jpg'
    WHEN 5 THEN '/product-images/product-05.jpg'
    WHEN 6 THEN '/product-images/product-06.jpg'
    WHEN 7 THEN '/product-images/product-07.jpg'
    WHEN 8 THEN '/product-images/product-08.jpg'
    WHEN 9 THEN '/product-images/product-09.jpg'
    WHEN 10 THEN '/product-images/product-10.jpg'
    WHEN 11 THEN '/product-images/product-11.jpg'
    WHEN 12 THEN '/product-images/product-12.jpg'
    WHEN 13 THEN '/product-images/product-13.jpg'
    WHEN 14 THEN '/product-images/product-14.jpg'
    WHEN 15 THEN '/product-images/product-15.jpg'
    WHEN 16 THEN '/product-images/product-16.jpg'
    WHEN 17 THEN '/product-images/product-17.jpg'
    WHEN 18 THEN '/product-images/product-18.jpg'
    WHEN 19 THEN '/product-images/product-19.jpg'
    WHEN 20 THEN '/product-images/product-20.jpg'
    WHEN 21 THEN '/product-images/product-21.jpg'
    WHEN 22 THEN '/product-images/product-22.jpg'
    WHEN 23 THEN '/product-images/product-23.jpg'
    WHEN 24 THEN '/product-images/product-24.jpg'
    WHEN 25 THEN '/product-images/product-25.jpg'
    WHEN 26 THEN '/product-images/product-26.jpg'
    WHEN 27 THEN '/product-images/product-27.jpg'
    WHEN 28 THEN '/product-images/product-28.jpg'
    WHEN 29 THEN '/product-images/product-29.jpg'
    WHEN 30 THEN '/product-images/product-30.jpg'
    ELSE main_image
END
WHERE id BETWEEN 1 AND 30
  AND name LIKE '演示%';
