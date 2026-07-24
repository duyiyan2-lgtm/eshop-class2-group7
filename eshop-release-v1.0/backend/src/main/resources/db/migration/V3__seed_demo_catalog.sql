INSERT INTO category (parent_id, name, sort_order, status)
SELECT NULL, '演示分类-手机数码', 10, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '演示分类-手机数码');

INSERT INTO category (parent_id, name, sort_order, status)
SELECT NULL, '演示分类-电脑办公', 20, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '演示分类-电脑办公');

INSERT INTO category (parent_id, name, sort_order, status)
SELECT NULL, '演示分类-家居生活', 30, 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '演示分类-家居生活');

INSERT INTO product (category_id, name, subtitle, main_image, detail, status)
SELECT
    (SELECT MIN(id) FROM category WHERE name = demo.category_name),
    demo.product_name,
    demo.subtitle,
    demo.main_image,
    demo.detail_text,
    'ON_SALE'
FROM (
    SELECT '演示分类-手机数码' category_name, '演示智能手机 A1' product_name, '高清屏幕与全天续航' subtitle, '/api/demo/phone.svg' main_image, '适合日常通讯、拍照与影音娱乐。' detail_text
    UNION ALL SELECT '演示分类-手机数码', '演示摄影手机 Pro', '大底主摄与光学防抖', '/api/demo/phone.svg', '面向摄影爱好者的影像旗舰手机。'
    UNION ALL SELECT '演示分类-手机数码', '演示轻薄手机 Mini', '轻巧机身，单手舒适', '/api/demo/phone.svg', '小尺寸轻薄设计，兼顾性能与手感。'
    UNION ALL SELECT '演示分类-手机数码', '演示旗舰手机 Ultra', '旗舰性能与高亮屏幕', '/api/demo/phone.svg', '高性能处理器与高刷新率显示屏。'
    UNION ALL SELECT '演示分类-手机数码', '演示长续航手机 Max', '大容量电池与快速充电', '/api/demo/phone.svg', '适合差旅和重度使用场景。'
    UNION ALL SELECT '演示分类-手机数码', '演示 5G 手机青春版', '流畅 5G，年轻配色', '/api/demo/phone.svg', '高性价比 5G 入门手机。'
    UNION ALL SELECT '演示分类-手机数码', '演示折叠屏手机 Fold', '大屏折叠，多任务办公', '/api/demo/phone.svg', '折叠形态兼顾便携与大屏体验。'
    UNION ALL SELECT '演示分类-手机数码', '演示游戏手机 Turbo', '高帧游戏与主动散热', '/api/demo/phone.svg', '为手游玩家设计的高性能手机。'
    UNION ALL SELECT '演示分类-手机数码', '演示商务手机 Elite', '安全、稳定、长续航', '/api/demo/phone.svg', '适合商务沟通和移动办公。'
    UNION ALL SELECT '演示分类-手机数码', '演示关怀智能手机 Care', '大字体与简洁模式', '/api/demo/phone.svg', '操作简单，适合长辈日常使用。'
    UNION ALL SELECT '演示分类-电脑办公', '演示轻薄笔记本 Air', '便携办公，全天续航', '/api/demo/computer.svg', '适合学习、会议和移动办公。'
    UNION ALL SELECT '演示分类-电脑办公', '演示游戏笔记本 G1', '独立显卡与高刷屏', '/api/demo/computer.svg', '适合大型游戏与内容创作。'
    UNION ALL SELECT '演示分类-电脑办公', '演示平板电脑 Pad', '学习娱乐二合一', '/api/demo/computer.svg', '支持影音、网课与轻办公。'
    UNION ALL SELECT '演示分类-电脑办公', '演示 27 英寸显示器', '高色域与护眼模式', '/api/demo/computer.svg', '适合办公设计与家庭娱乐。'
    UNION ALL SELECT '演示分类-电脑办公', '演示机械键盘 K87', '热插拔轴体与背光', '/api/demo/computer.svg', '紧凑布局机械键盘。'
    UNION ALL SELECT '演示分类-电脑办公', '演示无线鼠标 M2', '静音按键与多设备', '/api/demo/computer.svg', '适合办公室和移动使用。'
    UNION ALL SELECT '演示分类-电脑办公', '演示移动固态硬盘 1TB', '高速传输与轻巧机身', '/api/demo/computer.svg', '用于照片、视频和资料备份。'
    UNION ALL SELECT '演示分类-电脑办公', '演示千兆无线路由器', '双频 Wi-Fi 与稳定覆盖', '/api/demo/computer.svg', '适合家庭和小型办公室网络。'
    UNION ALL SELECT '演示分类-电脑办公', '演示家用打印机', '打印复印扫描一体', '/api/demo/computer.svg', '支持无线打印和移动端操作。'
    UNION ALL SELECT '演示分类-电脑办公', '演示高清摄像头', '自动对焦与双麦克风', '/api/demo/computer.svg', '适合视频会议和在线课堂。'
    UNION ALL SELECT '演示分类-家居生活', '演示智能台灯', '无频闪照明与触控调光', '/api/demo/home.svg', '适合阅读、学习和床头照明。'
    UNION ALL SELECT '演示分类-家居生活', '演示空气净化器', '高效过滤与空气监测', '/api/demo/home.svg', '改善卧室和客厅空气质量。'
    UNION ALL SELECT '演示分类-家居生活', '演示无线吸尘器', '轻量机身与强劲吸力', '/api/demo/home.svg', '适合地面、沙发和车内清洁。'
    UNION ALL SELECT '演示分类-家居生活', '演示智能电饭煲', '多种菜单与预约烹饪', '/api/demo/home.svg', '满足家庭日常煮饭和煲汤。'
    UNION ALL SELECT '演示分类-家居生活', '演示恒温电水壶', '多段控温与保温', '/api/demo/home.svg', '适合泡茶、咖啡和冲调饮品。'
    UNION ALL SELECT '演示分类-家居生活', '演示循环落地扇', '柔和送风与静音运行', '/api/demo/home.svg', '促进室内空气循环。'
    UNION ALL SELECT '演示分类-家居生活', '演示智能体脂秤', '多项身体数据分析', '/api/demo/home.svg', '通过手机查看体重与趋势。'
    UNION ALL SELECT '演示分类-家居生活', '演示蓝牙音箱', '立体声音效与长续航', '/api/demo/home.svg', '适合居家、露营和聚会。'
    UNION ALL SELECT '演示分类-家居生活', '演示智能摄像机', '高清夜视与移动侦测', '/api/demo/home.svg', '用于家庭看护和安全提醒。'
    UNION ALL SELECT '演示分类-家居生活', '演示安全插线板', '独立开关与过载保护', '/api/demo/home.svg', '适合家庭电器和电脑桌面供电。'
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
    SELECT '演示智能手机 A1' product_name, 'DEMO-PHONE-001' sku_code, '{"颜色":"深空黑","容量":"128GB"}' specs_json, 1999.00 price, 80 stock
    UNION ALL SELECT '演示摄影手机 Pro', 'DEMO-PHONE-002', '{"颜色":"星河银","容量":"256GB"}', 3299.00, 60
    UNION ALL SELECT '演示轻薄手机 Mini', 'DEMO-PHONE-003', '{"颜色":"云朵白","容量":"128GB"}', 1599.00, 90
    UNION ALL SELECT '演示旗舰手机 Ultra', 'DEMO-PHONE-004', '{"颜色":"曜石黑","容量":"512GB"}', 4999.00, 40
    UNION ALL SELECT '演示长续航手机 Max', 'DEMO-PHONE-005', '{"颜色":"森林绿","容量":"256GB"}', 2399.00, 70
    UNION ALL SELECT '演示 5G 手机青春版', 'DEMO-PHONE-006', '{"颜色":"晴空蓝","容量":"128GB"}', 1299.00, 120
    UNION ALL SELECT '演示折叠屏手机 Fold', 'DEMO-PHONE-007', '{"颜色":"典雅黑","容量":"512GB"}', 6999.00, 25
    UNION ALL SELECT '演示游戏手机 Turbo', 'DEMO-PHONE-008', '{"颜色":"竞速黑","容量":"256GB"}', 3599.00, 50
    UNION ALL SELECT '演示商务手机 Elite', 'DEMO-PHONE-009', '{"颜色":"商务灰","容量":"256GB"}', 2899.00, 55
    UNION ALL SELECT '演示关怀智能手机 Care', 'DEMO-PHONE-010', '{"颜色":"暖阳金","容量":"64GB"}', 899.00, 100
    UNION ALL SELECT '演示轻薄笔记本 Air', 'DEMO-PC-001', '{"内存":"16GB","硬盘":"512GB"}', 4599.00, 45
    UNION ALL SELECT '演示游戏笔记本 G1', 'DEMO-PC-002', '{"内存":"32GB","硬盘":"1TB"}', 7999.00, 30
    UNION ALL SELECT '演示平板电脑 Pad', 'DEMO-PC-003', '{"颜色":"银色","容量":"256GB"}', 2699.00, 65
    UNION ALL SELECT '演示 27 英寸显示器', 'DEMO-PC-004', '{"分辨率":"2K","刷新率":"165Hz"}', 1499.00, 75
    UNION ALL SELECT '演示机械键盘 K87', 'DEMO-PC-005', '{"轴体":"线性轴","配色":"深空灰"}', 399.00, 110
    UNION ALL SELECT '演示无线鼠标 M2', 'DEMO-PC-006', '{"颜色":"黑色","连接":"双模"}', 159.00, 150
    UNION ALL SELECT '演示移动固态硬盘 1TB', 'DEMO-PC-007', '{"容量":"1TB","接口":"USB-C"}', 699.00, 80
    UNION ALL SELECT '演示千兆无线路由器', 'DEMO-PC-008', '{"规格":"AX3000","颜色":"白色"}', 299.00, 95
    UNION ALL SELECT '演示家用打印机', 'DEMO-PC-009', '{"颜色":"白色","连接":"无线"}', 899.00, 35
    UNION ALL SELECT '演示高清摄像头', 'DEMO-PC-010', '{"分辨率":"1080P","颜色":"黑色"}', 259.00, 100
    UNION ALL SELECT '演示智能台灯', 'DEMO-HOME-001', '{"颜色":"白色","功率":"12W"}', 199.00, 120
    UNION ALL SELECT '演示空气净化器', 'DEMO-HOME-002', '{"适用面积":"45㎡","颜色":"白色"}', 1299.00, 50
    UNION ALL SELECT '演示无线吸尘器', 'DEMO-HOME-003', '{"颜色":"灰色","续航":"60分钟"}', 1599.00, 45
    UNION ALL SELECT '演示智能电饭煲', 'DEMO-HOME-004', '{"容量":"4L","颜色":"白色"}', 499.00, 70
    UNION ALL SELECT '演示恒温电水壶', 'DEMO-HOME-005', '{"容量":"1.5L","颜色":"米白"}', 229.00, 100
    UNION ALL SELECT '演示循环落地扇', 'DEMO-HOME-006', '{"颜色":"白色","档位":"12档"}', 399.00, 85
    UNION ALL SELECT '演示智能体脂秤', 'DEMO-HOME-007', '{"颜色":"白色","连接":"蓝牙"}', 129.00, 160
    UNION ALL SELECT '演示蓝牙音箱', 'DEMO-HOME-008', '{"颜色":"墨绿色","续航":"12小时"}', 299.00, 90
    UNION ALL SELECT '演示智能摄像机', 'DEMO-HOME-009', '{"分辨率":"2K","版本":"云台版"}', 269.00, 105
    UNION ALL SELECT '演示安全插线板', 'DEMO-HOME-010', '{"插孔":"6位","线长":"1.8米"}', 89.00, 200
) demo
WHERE NOT EXISTS (SELECT 1 FROM product_sku WHERE sku_code = demo.sku_code)
  AND EXISTS (SELECT 1 FROM product WHERE name = demo.product_name);
