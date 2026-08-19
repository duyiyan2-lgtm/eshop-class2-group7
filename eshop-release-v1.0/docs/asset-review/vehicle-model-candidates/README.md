# 车辆选配器候选模型审查（2026-08-18）

本目录只保存授权审查证据。本轮未下载任何 GLB/GLTF，也未替换项目模型。

查阅范围按指定顺序：Sketchfab 可下载 CC0/CC BY → Khronos glTF Sample Assets → BlenderKit 明确 CC0 → 带独立 LICENSE 的 GitHub 仓库。

## 推荐

**推荐候选：CL1M15 - Concept Sedan（Creator Lord，CC BY 4.0）**

理由：

1. Sketchfab 官方许可证字段就是 CC BY 4.0，不是 Standard / NC / SA / RF。
2. 四门三厢，虚构车名，预览无 OEM Logo。
3. 137k 三角面、约 9 MB GLB、26 个材质、PBR metalness，已经落在项目体积目标内。
4. 确认后只需登录 Sketchfab 走官方 Download，不必绕过任何限制。

若更在意“现代纯电轿车比例”，改选 **Polluxstar**（封闭前脸 + 贯穿灯 + 四门溜背），但要接受无贴图和约 40 MB 原始体积。

若更在意内饰和独立车轮绑定，改选 **MMC Generic Sedan**，但造型是 2010 年代燃油轿车，且标签含 ford/vw/kia。

## 未发现的东西

- 没有找到同时满足“CC0 + 写实四门纯电轿车 + 100k–500k 面 + 完整 PBR”的模型。
- Khronos 样本库只有当前正在用的 Car Concept（双门概念跑车）和 Toy Car（玩具车）。
- BlenderKit / Blendkit 上能搜到的汽车多为 Royalty Free 或带真实品牌，不符合本轮许可白名单。
- GitHub 上没有发现带独立 LICENSE、作者和来源说明、且质量足够的无品牌四门电轿 GLB。

## 明确排除（打开原页后排除）

| 对象 | 原因 |
|---|---|
| Unity Fan FREE Concept Car 003/004/006/007 | 描述写 CC0，但 Sketchfab **官方 license 字段是 Free Standard**，按要求不接受 |
| Tesla / Nio / BMW / Honda / Mazda / Audi / Ford / Togg 等 | 真实品牌，商标风险；部分还是 CC BY-NC-SA |
| Generic 4 door V8 Sports Car、ARCADE EV Pack | Sketchfab Store Royalty Free，禁止 |
| EV-ION Electric Car Concept | 页面无许可证、无 Download |
| 4-Doors Sedan with Interior（Tair Smailov） | Standard，且不可下载 |
| Chenzoss modern / luxury sedan | 预览带 Honda / BMW 风格厂标 |
| Generic Toyama XR Sedan | 标签含 subaru/wrx/impreza，商标风险高，且仅 25k 面 |
| Kenney Car Kit、Quaternius Cars | 许可证是合格 CC0，但是低模游戏套件，达不到选配器写实要求 |
| 任何 Xiaomi / SU7 / 游戏提取 / 官网抓取关键词 | 未搜索、未使用 |

## 下载方式（确认前不要执行）

三个候选都在 Sketchfab。官方下载需要登录账号，在模型页点 **Download 3D Model**。没有免登录直链。本轮没有调用 Sketchfab download API。
