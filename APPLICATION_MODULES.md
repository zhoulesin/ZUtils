# 应用模块与共享壳（Gradle 布局）

采用常见 **扁平多模块** 布局：可安装的 `Application` 与共享库目录均位于 `ZUtils-android/` 根下，与 `:app`、`:engine-core` 等同级，避免嵌套在自定义父目录中。

| 目录 / Gradle 模块 | 说明 |
|-------------------|------|
| **`app/`** `:app` | ZUtils 能力演示壳（`com.zhoulesin.zutils`），Raycast 风格 Compose UI。 |
| **`office-app/`** `:office-app` | ZOffice 产品壳（`com.zhoulesin.zoffice`），独立主题与界面，与 `:app` 不共用 Composable。 |
| **`application-shell/`** `:application-shell` | 共享宿主：引擎引导、本地函数、Room、自动化、`agent` 包等；**不含**任一产品的根 Compose 界面。 |

## 依赖约定

- `:app`、`:office-app` → `implementation(project(":application-shell"))`（或按需 `api`）。
- 新垂直产品：在 **`ZUtils-android/` 根下** 新建 `*-app/` 模块，在 `settings.gradle.kts` 中 `include(":<name>")`，并依赖 `:application-shell`。

## 与文档的对应

- 日期时间等跨端约定见仓库根目录 `docs/contracts/`。
