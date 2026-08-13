# E-Shop Android 客户端技术方案

## 一、方案结论

Android 客户端采用 **Vue 3 + Vant + Capacitor 8 + Android Studio**。

选择 Capacitor 的核心原因是项目已经拥有完整的手机 H5 商城。Capacitor 可以直接复用商品、购物车、地址、下单、支付、订单和个人中心页面，同时生成标准 Android Studio 工程；相比重新用 Kotlin 或 Flutter 实现全部业务，开发周期更短、接口和页面逻辑不会出现两套实现，也更适合当前项目验收阶段。

## 二、GitHub 技术项目

| 项目 | 当前用途 | 采用原因 |
| --- | --- | --- |
| [ionic-team/capacitor](https://github.com/ionic-team/capacitor) | Web 与 Android 原生桥接、生成原生工程 | 官方维护，可接入现有 Vue 项目，支持 Android/iOS 与原生插件 |
| [ionic-team/capacitor-plugins](https://github.com/ionic-team/capacitor-plugins) | 返回键、网络、状态栏、启动页 | 与 Capacitor 主版本兼容，减少第三方插件质量风险 |
| [ionic-team/capacitor-assets](https://github.com/ionic-team/capacitor-assets) | 生成多密度图标和启动画面 | 一份 SVG 自动生成 Android 所需资源，避免手工遗漏尺寸 |
| [github/gitignore](https://github.com/github/gitignore) | Android 构建产物忽略规则 | 防止 APK、Gradle 缓存和本地 SDK 路径误提交 |

## 三、工程结构

```text
eshop-release-v1.0/frontend/
├─ android/                 Android Studio 原生工程
├─ assets/                  图标与启动页源文件
├─ src/native/runtime.js    原生生命周期和插件适配
├─ .env.android             Android 正式 API 地址
├─ capacitor.config.json    Capacitor 应用配置
└─ ANDROID.md               构建与出包说明
```

## 四、安全和运行策略

1. APK 内置编译后的 Vue 页面，不配置生产 `server.url`，避免应用完全退化为远程网页壳。
2. 所有后端请求只使用 `https://eshop.zhuyiyuan9.top/api`，Android 清单禁止明文 HTTP。
3. 启用 Capacitor 原生 HTTP 适配现有 Axios 请求，避免 WebView Origin 导致跨域登录失败。
4. 登录令牌继续沿用当前前端会话机制，启动时由现有 `/auth/me` 校验账号状态和角色。
5. 关闭 Android 自动备份，避免应用私有数据进入系统备份。
6. 正式发布必须使用独立 `.jks` 密钥签名，密钥和密码只由组长保管，不提交仓库。

说明：`capacitor-assets` 只在首次生成资源时使用；生成后的 Android 图标和启动画面已纳入工程。由于该工具当前仍包含仅用于构建的旧图像处理依赖，因此没有保留为日常 npm 依赖，避免影响依赖安全审计。

## 五、版本与兼容性

- Capacitor：8.5.0
- Android minSdk：24（Android 7.0）
- Android target/compileSdk：36
- 应用 ID：`top.zhuyiyuan9.eshop`
- 当前版本：1.0.0（versionCode 1）

## 六、测试范围

正式提交 APK 前需要在一台 Android 真机和一台模拟器完成：首次启动、登录、商品搜索、详情与 SKU、购物车、地址、下单、模拟支付、订单、评价、断网恢复、物理返回键、横竖屏和不同分辨率测试。
