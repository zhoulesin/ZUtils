# 开发记录 — 2026-04-25

## 今日改动

### 模块重构

新增 **`plugin-manager`** 模块，将 DEX 加载和版本管理从 app 模块中抽离：

```
engine-core → plugin-manager → app
(接口+引擎)   (DEX 加载+版本)   (UI+内置函数)
```

### DEX 资产迁移

`app/src/main/assets/dex/` → `plugin-manager/src/main/assets/dex/`

### 版本管理

- `DexSpec.dependencies` 从 `List<String>` 改为 `List<DependencySpec>`，每个依赖独立记录 name + version
- `ZFunction` 新增 `requiredDependencies: Map<String, String>`，插件内部声明所需依赖版本
- DEX 文件名含版本号：`plugin_qrcode_v1.0.0.dex`、`lib_zxing_v3.5.3.dex`
- Engine 加载 DEX 后校验版本一致性，日志中展示版本信息
- `dex_manifest.json` 统一管理版本映射

### 异常处理

- `Engine.resolveMissingFunctions()` 中 `download()` 和 `load()` 加了 try-catch，DEX 文件缺失时不崩溃，以日志展示错误

### DEX 文件问题修复

`lib_zxing_v3.5.3.dex` 因生成失败曾是 0 字节空文件，导致两类错误：
- `InMemoryDexClassLoader` → "Bad range"
- `DexClassLoader` → "Not allowed"

重新生成 454KB 后恢复正常。

### 自动化构建

`scripts/build-dex.sh` — 一键构建 DEX 并输出到 `plugin-manager/src/main/assets/dex/`。

### 文档更新

- `README.md` — 项目根 README 更新
- `engine-core/README.md` — 模块依赖关系 + DEX 生成说明
- `plugin-manager/README.md` — 新建，包含 DEX 加载和升级流程
- `TestFun/README.md` — 版本管理 + 构建方式

## 当前模块结构

```
ZUtils/
├── engine-core/                    # 核心引擎（18 个文件）
│   ├── core/      ZFunction, FunctionInfo, ZResult, Parameter, 
│   │             ExecutionContext, FunctionRegistry, FunctionSource,
│   │             PermissionCheck, PermissionChecker (9)
│   ├── workflow/  Workflow, WorkflowResult, WorkflowEngine, 
│   │             DefaultWorkflowEngine (4)
│   ├── dex/       DexLoader, DexSpec, DependencySpec (2)
│   ├── llm/       LlmClient (1)
│   └── Engine.kt
├── plugin-manager/                 # 插件管理（1 个文件 + assets）
│   └── DefaultDexLoader.kt
├── TestFun/                        # DEX 插件（1 个文件）
│   └── QRCodeFunction.kt
└── app/                            # 主应用（16 个文件）
    └── engine/functions/           (15 个内置函数)
    └── MainActivity.kt
```
