# E-Shop Android 客户端

本目录已使用 Capacitor 8 将现有 Vue 3 + Vant 手机商城转换为标准 Android 工程。应用会把前端静态资源打入 APK，并通过 HTTPS 调用正式后端 API，不是直接显示远程网页的简单 WebView 壳。

## 已接入能力

- Android 应用 ID：`top.zhuyiyuan9.eshop`
- 默认入口：`/m/products`
- 正式 API：`https://eshop.zhuyiyuan9.top/api`
- 原生启动画面、自适应桌面图标、深浅色启动资源
- Android 物理返回键：首页最小化、业务页面返回上一页
- 原生网络状态监听与断网提示
- 状态栏与安全区域适配
- Capacitor 原生 HTTP，避免 Android WebView 跨域限制
- 禁止 HTTP 明文流量、关闭 Android 自动备份

## 开发和同步

```powershell
cd frontend
npm install
npm run android:sync
npm run android:open
```

`android:open` 会启动 Android Studio。首次打开时等待 Gradle 与 Android SDK 36 同步完成，然后选择模拟器或已开启 USB 调试的真机运行。

如果命令行提示 `SDK location not found`，表示本机还没有配置 Android SDK。请先安装 Android Studio，在首次启动向导中安装 Android SDK 36；再用 Android Studio 打开 `frontend/android`。Android Studio 会自动生成仅限本机使用的 `android/local.properties`，不要把该文件提交到 Git。

## 生成调试 APK

安装 Android Studio 和 Android SDK 后执行：

```powershell
npm run android:apk:debug
```

输出文件：

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

## 生成正式签名包

在 Android Studio 中选择 `Build > Generate Signed App Bundle or APK`，首次创建 `.jks` 密钥库后选择 APK 或 AAB。密钥库和密码不得提交到 Git。

## 修改后如何更新 Android 工程

每次修改 Vue 页面后只需运行：

```powershell
npm run android:sync
```

该命令会重新构建 Android 专用前端并复制到原生工程，无需重新创建 `android` 文件夹。

## Android 专属页面布局

- 首页：频道切换、品牌搜索框、四宫格快捷入口、活动区、热销榜和双列商品流。
- 购物车：独立标题、管理模式、活动/地址工具、商城分组、商品卡片和固定结算栏。
- 个人中心：账户头图、地址/客服/设置、订单状态快捷入口、收藏/足迹/评价等常用服务。
- 原生安全区：状态栏采用浅色背景和深色文字，底部导航、结算栏会避开系统手势区域。

以上增强只在 Android 构建（`.env.android`）或原生容器内启用，不改变 PC 端、管理端和浏览器 H5 的入口逻辑。

应用图标和启动画面已经生成并保存在 Android 工程中；`assets/` 下的 SVG 是后续换图时使用的设计源文件。当前不把一次性资源生成器作为项目依赖，避免仅在构建阶段使用的旧图像处理依赖进入日常安装和安全审计范围。

## 技术来源

- Capacitor：https://github.com/ionic-team/capacitor
- 官方原生插件：https://github.com/ionic-team/capacitor-plugins
- 图标与启动资源工具：https://github.com/ionic-team/capacitor-assets
- Android 工程忽略规则：https://github.com/github/gitignore/blob/HEAD/Android.gitignore
