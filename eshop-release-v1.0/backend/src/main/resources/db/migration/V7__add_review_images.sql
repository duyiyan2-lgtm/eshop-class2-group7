-- 评价晒图：以 JSON 数组字符串保存图片 URL，兼容旧评价（NULL / 空数组）
ALTER TABLE product_review
    ADD COLUMN images_json VARCHAR(2000) NULL COMMENT '评价图片URL JSON数组，最多3张' AFTER content;
