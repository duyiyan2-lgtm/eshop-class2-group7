# 课程演示车辆素材说明

本目录图片仅用于 E-Shop 课堂项目演示，**不是小米汽车官方素材**。

## 来源

| 文件 | 来源 | 用途 |
| --- | --- | --- |
| `su7/*.svg` | 自制矢量剪影（轿车轮廓） | 新一代 SU7 演示主图/视角 |
| `yu7/*.svg` | 自制矢量剪影（SUV 轮廓） | YU7 演示主图/视角 |
| `ultra/*.svg` | 自制矢量剪影（低趴性能轿车轮廓） | SU7 Ultra 演示主图/视角 |

- 禁止热链 `xiaomiev.com` 或官网 CDN。
- 禁止使用官方商标图形、官方实车摄影或 3D 资源。
- 后续若替换为授权实拍/自制渲染，请同步更新本文件。

## 目录约定

```
vehicles/<car>/<color>/{front45,side,rear45,rear,front}.webp
vehicles/shared/wheels/{w19,w20,w21}.webp
vehicles/shared/interiors/{black,beige,sport}.webp
vehicles/shared/packs/{winter,comfort,track}.webp
vehicles/placeholder.svg
```

颜色目录：`white` / `black` / `silver` / `green`。
视角：前 45°、正侧、后 45°、正后、正前。

## 来源（2026-08-17 重构）

高清图为 AI 生成的**通用高端纯电轿车 / SUV 课程演示图**，不是小米官方摄影或 3D 资源。
禁止热链 `xiaomiev.com`。旧版卡通 SVG 仅作失败回退，不再作为舞台主图。

缺少某颜色+视角组合时，前端按
`颜色+视角 → 该颜色 front45 → 白色该视角 → 白色 front45`
回退。

当前覆盖（2026-08-18）：

| 车型 | 白 | 黑 | 银 | 绿 |
| --- | --- | --- | --- | --- |
| su7 | 5 视角齐全 | 5 视角齐全 | 5 视角齐全 | 5 视角齐全 |
| yu7 | 5 视角 | front45 + side | front45 + side | front45 + side |
| ultra | 5 视角 | front45 + side | front45 + side | front45 + side |

旧版卡通 SVG 已删除，仅保留 `placeholder.svg` 作为加载失败剪影。
