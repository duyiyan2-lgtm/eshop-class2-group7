# E-Shop PC 桌面客户端

本目录使用 Electron 将现有 E-Shop Web 商城封装为 Windows PC 桌面应用。它复用 Vue 页面和 Spring Boot API，不复制业务代码，也不改变 PC、H5、商家端和管理端现有部署。

## 默认连接地址

```text
https://eshop.zhuyiyuan9.top/pc/products
```

桌面端需要联网访问 E-Shop 服务器。服务器发布新功能后，重新打开客户端即可使用，一般不需要重新制作安装包。

## 本地调试

先确认 Docker 项目已在 `http://127.0.0.1:8088` 启动，再在 PowerShell 中执行：

```powershell
cd C:\Users\duyiyan\Desktop\eshop-class2-group7\eshop-release-v1.0\desktop
npm install
$env:ESHOP_DESKTOP_URL = 'http://127.0.0.1:8088/pc/products'
npm start
```

关闭当前 PowerShell 后，临时环境变量会自动失效。未设置 `ESHOP_DESKTOP_URL` 时，客户端连接线上正式地址。

## 生成 Windows EXE

生成免安装版：

```powershell
npm run dist:win
```

生成带安装向导的版本：

```powershell
npm run dist:win:installer
```

生成文件位于：

```text
desktop\dist\
```

## 已加入的桌面能力

- E-Shop 品牌启动页与加载动画；
- 自定义 Windows EXE、任务栏和安装程序图标；
- 单实例运行，重复启动时聚焦已有窗口；
- 登录状态与 Cookie 持久保存；
- PC 商城首页、统一入口、刷新、缩放和全屏菜单；
- 断网或服务器不可用时显示 E-Shop 风格错误页；
- 外部链接交给系统浏览器打开；
- 禁用 Node 注入、WebView 和网页权限请求，保持 Web 与桌面系统隔离。

## 验收建议

1. 登录买家账号并重启客户端，确认登录状态保留；
2. 完成商品浏览、购物车、地址、下单、模拟支付和订单查询；
3. 断开网络后启动客户端，确认出现重试页面；
4. 恢复网络并单击“重新连接”，确认商城恢复；
5. 检查窗口缩放、最小化、最大化和单实例行为。
