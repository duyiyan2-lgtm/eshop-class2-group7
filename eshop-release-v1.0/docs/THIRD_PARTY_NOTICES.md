# 第三方素材声明（课程演示汽车 3D）

本目录记录车辆选配器三维预览所使用的第三方模型、环境和解码器。运行时全部从本仓库本地路径加载，不热链接外部资源。

## 2022 Bugatti La Voiture Noire

- **素材名称**：2022 Bugatti La Voiture Noire
- **作者**：OUTPISTON（Sketchfab 用户名：`outpiston`）
- **原始页面**：https://sketchfab.com/3d-models/2022-bugatti-la-voiture-noire-b99b24064dfe40a9b358ac633a5448f2
- **模型 UID**：`b99b24064dfe40a9b358ac633a5448f2`
- **许可证**：Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International（CC BY-NC-SA 4.0）  
  https://creativecommons.org/licenses/by-nc-sa/4.0/
- **用途限制**：只用于本课程项目的非商业展示；不得用于商业用途。品牌名称和商标不因 CC 许可而获得授权。
- **本地路径**：`frontend/public/models/vehicles/bugatti-la-voiture-noire/BugattiLaVoitureNoire.draco.glb`
- **修改说明**：从作者通过 Sketchfab 官方下载功能提供的 GLB 制作 Web 派生版本；使用 glTF Transform 进行 Draco 网格压缩，未对车型设计进行实质改造。原始文件 64.21 MiB，Web 版 2.72 MiB。
- **Web 版 SHA-256**：`45961FAFC97BEA29447D64C80F0CF987568F2BBFFCF61B5B51CA3899C7B50795`
- **署名文本**：`“2022 Bugatti La Voiture Noire” by OUTPISTON, via Sketchfab, licensed under CC BY-NC-SA 4.0; modified with Draco compression.`

该派生模型按 CC BY-NC-SA 4.0 的相同方式共享。项目代码的其他部分不因此自动变更许可证。

## Car Concept

- **素材名称**：Car Concept
- **用途**：课程演示通用概念车 GLB，非小米汽车官方模型
- **原始地址**：https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/CarConcept
- **下载文件**：https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/CarConcept/glTF-Binary/CarConcept.glb
- **作者**：Eric Chadwick / Darmstadt Graphics Group GmbH（© 2024）
- **基础网格**：Unity Fan，《Concept Car 004》，CC0  
  https://sketchfab.com/3d-models/free-concept-car-004-public-domain-cc0-4cba124633eb494eadc3bb0c4660ad7e
- **许可证**：Creative Commons Attribution 4.0 International（CC BY 4.0）
- **商标**：模型中含 Khronos / 3D Commerce 标识，权属 Khronos Group，仅随官方 Sample Asset 一并展示
- **本地路径**：`frontend/public/models/vehicles/car-concept/CarConcept.glb`
- **修改说明**：未改网格与贴图。课程项目仅在运行时改写车漆/内饰/轮毂/卡钳材质颜色，并缩放轮毂节点以区分 W19/W20/W21。页面保留“非小米汽车官方页面，仅用于课程项目演示”提示。

## studio_small_09 HDRI

- **素材名称**：studio_small_09
- **用途**：车漆环境反射（PMREM）
- **原始地址**：https://polyhaven.com/a/studio_small_09
- **作者**：Poly Haven
- **许可证**：CC0 1.0 Universal
- **本地路径**：`frontend/public/models/env/studio_small_09_1k.hdr`
- **修改说明**：使用官方 1K HDR，未再改像素。仅作为 `scene.environment`，不作为舞台背景图，避免出现矩形相框。

## Draco / Basis 解码器

- **素材名称**：three.js 附带 Draco glTF decoder、Basis Universal transcoder
- **原始地址**：three.js `examples/jsm/libs/draco/gltf` 与 `examples/jsm/libs/basis`
- **许可证**：随 three.js（MIT）及 Google Draco / Binomial Basis 各自开源许可
- **本地路径**：
  - `frontend/public/models/decoders/draco/`
  - `frontend/public/models/decoders/basis/`
- **修改说明**：原样复制，供 `DRACOLoader` / `KTX2Loader` 使用。当前 Car Concept GLB 未使用 Draco/KTX2，解码器预留兼容。

## MeshoptDecoder

- **来源**：`three/examples/jsm/libs/meshopt_decoder.module.js`
- **许可证**：随 three.js 分发的 MeshOptimizer 解码器
- **修改说明**：通过动态 import 打进独立异步 chunk，不热链接 CDN。
