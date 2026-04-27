# ZUtils — 项目总结

## 项目定位

AI 驱动的 Android 动态功能引擎。用户用自然语言描述需求，AI 拆解为工作链，顺序调用 App 函数。若函数缺失，通过 DEX 动态加载补全能力。

## 核心架构

### 模块划分

```
┌─────────────────────────────────────────────────────┐
│                    app (UI + 内置函数)                │
│  Compose UI · 15 个内置函数 · 工作流构建器 · 设置    │
├─────────────────────────────────────────────────────┤
│              plugin-manager (插件管理)                │
│  DEX 加载 + 版本管理 + LLM 客户端 + 权限管理          │
├─────────────────────────────────────────────────────┤
│                engine-core (核心引擎)                  │
│  Engine · ZFunction · WorkflowEngine · Registry      │
│  DexLoader · LlmClient · PermissionChecker           │
├─────────────────────────────────────────────────────┤
│  functions/     engine-core     qrcode-function     │
│  (独立函数模块)   (核心接口)      (DEX 插件)          │
└─────────────────────────────────────────────────────┘
```

### 依赖方向

```
app → plugin-manager → engine-core
                     ↙
        TestFun / qrcode-function
```

### 核心数据流

```
用户输入
  ├─ 关键词匹配 (parseQuery) — 无 LLM 时的 fallback
  └─ LLM 解析 (VolcengineLlmClient)
       ↓
  Workflow (steps: WorkflowStep[])
       ↓
  Engine.execute()
       ├─ resolveMissingFunctions() → DexLoader → 动态加载 DEX
       └─ DefaultWorkflowEngine.execute()
            ├─ 顺序模式: 独立执行每一步
            └─ 管道模式: 上一步结果 → pipeline 注入下一步
       ↓
  WorkflowResult → UI 展示
```

---

## 已实现功能

### 1. 引擎框架

| 组件 | 说明 |
|------|------|
| `Engine` | 顶层 Facade：持有 Registry + WorkflowEngine + DexLoader + LlmClient |
| `ZFunction` | 函数接口：`info` + `execute(context, args)` + `requiredDependencies` |
| `FunctionRegistry` | 函数注册中心：register / get / getAllInfos / contains |
| `WorkflowEngine` | 工作流执行引擎：支持顺序 + 管道两种模式 |
| `DexLoader` | DEX 动态加载接口 |
| `LlmClient` | LLM 对接接口 |

### 2. 内置函数（16 个）

| 类别 | 函数 | 说明 |
|------|------|------|
| 计算 | `calculate` | 四则运算（全角/半角符号兼容） |
| 系统 | `getCurrentTime`, `getBatteryLevel`, `getDeviceInfo`, `getScreenInfo`, `getStorageInfo`, `getNetworkType` | 设备信息查询 |
| 工具 | `uuid`, `base64`, `getClipboard`, `setClipboard` | 通用工具 |
| 控制 | `setScreenBrightness`, `setVolume`, `getVolume` | 设备控制 |
| UI | `toast` | 消息提示（接受任意入参） |
| DEX 演示 | `generateQRCode` | 二维码生成（zxing） |

每个函数标注了 `outputType`（TEXT / NUMBER / OBJECT / NONE）用于管道流匹配。

### 3. 交互 UI

- **执行页**：输入框 + 结果列表，支持展开/收起
- **函数页**：展示全部注册函数（名称/描述/参数/来源/权限）
- **工作流页**：
  - 预置工作流（6 顺序 + 4 管道）
  - 自定义工作流构建器
  - Room 持久化（存储/删除/列表刷新）
  - 执行时首个函数有必填参数 → 弹出输入框

### 4. DEX 动态加载

- `dex_manifest.json` 管理版本映射
- `DefaultDexLoader`：从 assets → InMemoryDexClassLoader / DexClassLoader
- 多 DEX classpath（插件 DEX + 依赖 DEX）
- 加载后校验 `requiredDependencies` 版本一致性
- 异常时 Logcat `ZUtils-DEX` 输出完整错误链

### 5. LLM 集成

- `VolcengineLlmClient`：火山引擎方舟 /api/coding/v3 端点
- Function Calling / Tool Use 协议
- 16 个函数自动转 tools 参数
- 调用失败回退到关键词匹配
- API Key 通过 UI 设置，存 SharedPreferences

### 6. 权限管理

- `PermissionChecker`：检查 Manifest 声明 + 运行时授权状态
- 特殊权限（`WRITE_SETTINGS`）使用 `Settings.System.canWrite()` 检测
- 未授权时显示「去授权」按钮 → 确认弹窗 → 跳转系统设置

---

## 设计模式

| 模式 | 应用 |
|------|------|
| **Facade** | `Engine` 统一对外接口，隐藏 Registry / WorkflowEngine / DexLoader / LlmClient |
| **Strategy** | WorkflowEngine 接口，DefaultWorkflowEngine 实现 |
| **Registry** | FunctionRegistry 管理所有函数的注册与查找 |
| **Chain of Responsibility** | Workflow 步骤按顺序执行，中断即停止 |
| **Template Method** | BaseDexLoader 定义加载骨架，子类实现具体逻辑 |
| **Plugin / Dynamic Loading** | DEX 动态加载实现插件式扩展 |
| **Tool Calling (LLM)** | LLM 返回结构化函数调用，引擎执行后回传结果 |

## 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin 2.1.10 | 语言 |
| Jetpack Compose + Material 3 | UI |
| AGP 9.1.1 | 构建 |
| Room 2.7.1 + KSP | 持久化 |
| kotlinx-serialization | JSON 序列化 |
| OkHttp 4.12 | HTTP 客户端 |
| ZXing 3.5.3 | 二维码生成 |
| InMemoryDexClassLoader | DEX 内存加载 |
| Mockito 5.4 | 单元测试 |
| Gradle 9.3.1 | 构建系统 |
