# PRD: ZUtils - AI 驱动的 Android 动态功能引擎

## 1. 产品概述

### 1.1 愿景
打造一个纯 AI 驱动的 Android 应用。用户用自然语言描述需求，AI 理解意图、拆解为可执行工作链，动态加载所需能力并执行，最终交付结果。

### 1.2 核心理念
```
用户需求 → LLM 解析 → 工作链(Workflow) → 顺序调用 App 函数
                                          ↓ (函数缺失)
                                    动态加载 DEX → 继续执行
```

### 1.3 产品定位
一个"可生长的"Android 应用——能力不局限于安装包内的代码，而是通过 AI + 动态加载机制无限扩展。

---

## 2. 用户故事

### P0 (MVP)
- **US-01**: 用户输入文字需求，App 返回结果
- **US-02**: 用户说出"帮我计算 2+2"，App 返回 4
- **US-03**: 用户说出"获取当前系统时间"，App 返回格式化时间
- **US-04**: 用户说出"把屏幕亮度调到 50%"，App 执行并反馈
- **US-05**: 系统在 App 内找不到所需函数时，从远端下载 DEX 并加载调用

### P1
- **US-06**: 用户说出"读取联系人列表"，App 读取并展示
- **US-07**: 工作链包含多个步骤（如"先获取位置，再搜索附近的咖啡店，然后发短信给老婆"）
- **US-08**: 函数调用失败时回滚或给出可读的错误提示
- **US-09**: 支持语音输入需求

### P2
- **US-10**: 用户编写自定义函数，上传为 DEX，扩展 App 能力
- **US-11**: 工作链可视化：用户看到 AI 拆解出的步骤列表
- **US-12**: 函数市场：社区共享 DEX 插件

---

## 3. 系统架构

```
┌─────────────────────────────────────────────────────┐
│                  用户界面层 (UI Layer)                │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │ 输入面板  │  │ 结果展示  │  │ 工作链可视化       │ │
│  │文本/语音  │  │Markdown  │  │步骤列表/状态       │ │
│  └──────────┘  └──────────┘  └───────────────────┘ │
├─────────────────────────────────────────────────────┤
│                  编排层 (Orchestrator)               │
│  ┌──────────────────┐  ┌─────────────────────────┐  │
│  │  LLM Client       │  │  Workflow Engine        │  │
│  │  - 需求理解       │  │  - 步骤拆分             │  │
│  │  - 函数选择       │  │  - 顺序/条件/循环执行   │  │
│  │  - 参数提取       │  │  - 状态管理             │  │
│  │  - 结果摘要       │  │  - 错误处理/重试        │  │
│  └──────────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│                  能力层 (Capability Layer)           │
│  ┌──────────────────┐  ┌─────────────────────────┐  │
│  │  Function Registry│  │  Dex Loader             │  │
│  │  - 本地函数注册   │  │  - 远端 DEX 下载        │  │
│  │  - 签名信息       │  │  - 类加载/反射调用      │  │
│  │  - 元数据描述     │  │  - 缓存 & 校验          │  │
│  └──────────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│                  基础设施层                           │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │ 网络模块  │  │ 本地存储  │  │ 权限管理          │ │
│  │OkHttp    │  │Room/DSL  │  │Android Permissions│ │
│  └──────────┘  └──────────┘  └───────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## 4. 核心模块设计

### 4.1 LLM Client

**职责**: 与大模型 API 交互，完成自然语言到结构化指令的转换

**关键能力**:
- 支持对接多种 LLM 后端（OpenAI / Claude / 本地模型等）
- 使用 Function Calling (工具调用) 模式，而非纯文本对话
- 将用户需求拆解为 `WorkflowStep[]`

**Prompt 设计要点**:
```
System Prompt:
你是一个 Android 功能编排助手。你的任务是将用户的需求拆解为一个有序的
函数调用链。每个函数调用包含：函数名、参数列表。

可用的函数列表如下：
{{function_registry}}

请返回 JSON 格式的工作链：
{
  "steps": [
    { "function": "func_name", "args": {...} },
    ...
  ]
}
```

### 4.2 Workflow Engine

**职责**: 解析 LLM 返回的工作链，顺序调用函数，管理执行上下文

```kotlin
data class WorkflowStep(
    val function: String,
    val args: Map<String, Any?>,
    val dependsOn: List<Int> = emptyList() // 步骤依赖
)

data class WorkflowContext(
    val steps: List<WorkflowStep>,
    val results: MutableMap<Int, Any?>,
    val errors: MutableMap<Int, String>,
    var currentIndex: Int = 0
)
```

**执行流程**:
```
1. 接收 LLM 返回的 WorkflowStep[]
2. 校验每个函数是否在 Registry 中注册
3. 若缺失 → 触发 DexLoader 动态加载
4. 顺序执行每个步骤，传递上下文
5. 全部完成 → 汇总结果交给 LLM 生成总结
6. 返回给用户
```

### 4.3 Function Registry

**职责**: 管理所有可用函数的注册、查找、调用

```kotlin
interface ZFunction {
    val name: String
    val description: String
    val parameters: List<Parameter>  // JSON Schema 格式
    suspend fun execute(context: Any?, args: Map<String, Any?>): Any?
}
```

**内置函数示例** (MVP):
| 函数名 | 描述 | 参数 |
|--------|------|------|
| `calculate` | 数学计算 | `expression: string` |
| `getCurrentTime` | 获取当前时间 | `format: string (可选)` |
| `setScreenBrightness` | 设置屏幕亮度 | `level: number (0-100)` |
| `getBatteryLevel` | 获取电池电量 | - |
| `toast` | 显示 Toast | `message: string` |

### 4.4 Dex Loader

**职责**: 动态加载未内置的 Android 功能

```kotlin
interface DexLoader {
    // 根据函数名查找缺失的 DEX
    fun resolve(functionName: String): DexSpec?
    // 下载 DEX (从远端或本地缓存)
    suspend fun download(spec: DexSpec): File
    // 加载 DEX 并注册其中的函数
    fun load(dexFile: File): List<ZFunction>
}
```

**DEX 规范**:
- DEX 文件内包含实现了 `ZFunction` 接口的类
- 每个 DEX 文件可以包含多个函数
- 附带元数据文件 `functions.json` 描述所含函数

**远端 DEX 仓库结构**:
```
/dex-repo/
  /com.example.foo/
    v1/
      functions.json   # 函数元数据
      plugin.dex       # DEX 文件
      signature.sig    # 签名
    v2/
      ...
```

### 4.5 安全沙箱

**职责**: 确保动态加载的代码运行在受控环境中

- DEX 来源校验（签名验证）
- 函数权限声明（该函数需要哪些 Android 权限）
- 用户确认弹窗（首次调用敏感功能时）
- 超时/异常隔离（单个函数 Crash 不影响引擎）

---

## 5. 典型工作流示例

### 场景: "关掉蓝牙，把亮度调到最低，然后告诉我当前电量"

```
Step 1: LLM 解析
        ↓
{
  "steps": [
    { "function": "setBluetoothEnabled", "args": { "enabled": false } },
    { "function": "setScreenBrightness", "args": { "level": 0 } },
    { "function": "getBatteryLevel",    "args": {} }
  ]
}

Step 2: Registry 查找
        ↓
- setBluetoothEnabled → 未找到！
- setScreenBrightness → 命中内置
- getBatteryLevel → 命中内置

Step 3: DexLoader 解析缺失
        ↓
从远端下载包含 setBluetoothEnabled 的 DEX
校验签名 → 加载 → 注册到 Registry

Step 4: Workflow Engine 顺序执行
        ↓
[1/3] setBluetoothEnabled(false) → OK
[2/3] setScreenBrightness(0)     → OK
[3/3] getBatteryLevel()          → 85%

Step 5: LLM 总结
        ↓
"已关闭蓝牙，亮度调到最低，当前电量 85%。"
```

---

## 6. 阶段性规划 (Roadmap)

### Phase 1: Foundation (2-3 周)
- 项目架构搭建
- Function Registry + 基础内置函数（calculate, getTime, toast）
- 简单的 Workflow Engine（纯顺序执行，无依赖）
- LLM Client 对接（先写死 API Key + OpenAI）
- 输入/输出 UI 面板
- 用户说一句话 → 执行一个函数 → 返回结果

### Phase 2: Dynamic Loading (2 周)
- DexLoader 核心实现
- 远端 DEX 仓库（简单 JSON 列表 + 下载）
- 缺失函数自动下载 + 动态加载 + 注册
- 函数签名校验
- 支持多步骤工作链

### Phase 3: 能力增强 (2 周)
- 更多内置函数（蓝牙、WiFi、音量、通知等）
- 工作链可视化（步骤列表 + 执行状态）
- 参数提取增强（LLM 更精准的参数抽取）
- 错误处理 & 重试机制

### Phase 4: 生态 & 体验 (持续)
- 语音输入
- 函数市场 / DEX 上传
- 本地 LLM 支持（通过 ML Kit 或 llama.cpp 等）
- 自定义函数编写 & 热加载
- 权限申请流程优化

---

## 7. 关键技术决策

| 决策 | 方案 | 理由 |
|------|------|------|
| LLM 通信方式 | Function Calling API | 结构化输出，无需自己写 Parser |
| 本地函数定义 | Kotlin Interface + 注解 | 类型安全，IDE 友好 |
| DEX 加载 | `DexClassLoader` | Android 原生支持，成熟稳定 |
| 函数协议 | JSON Schema 描述参数 | LLM 原生支持，通用性强 |
| 网络层 | OkHttp + Retrofit | 成熟、广泛使用 |
| 序列化 | Kotlinx Serialization | 原生 Kotlin 支持，性能好 |
| 状态管理 | Compose State + ViewModel | 与现有 UI 框架一致 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM 输出不稳定（JSON 格式错误） | 工作链解析失败 | 校验 + 重试 + fallback 提示 |
| 动态加载的 DEX 性能问题 | 执行卡顿 | 异步加载，缓存机制 |
| 安全风险（恶意 DEX） | 隐私泄露 | 签名校验 + 权限隔离 + 用户确认 |
| Android 权限限制 | 函数无法执行 | 运行时权限申请流程，引导用户 |
| LLM API 延迟 | 用户体验差 | 流式输出，Loading 状态 |
| DEX 兼容性（不同 Android 版本） | 加载失败 | 兼容 minSdk 24，充分测试 |

---

## 9. 非功能性需求

- **冷启动首次执行**: < 3 秒（API + 加载时间）
- **DEX 加载**: < 1 秒（本地缓存）
- **APK 体积**: 基础包 < 10MB（DEX 按需下载）
- **支持 Android 7.0+**（minSdk = 24）
- **离线基本能力**: 内置函数可在无网络下执行

---

## 10. 未来畅想

- **跨应用能力**: 通过 Android Accessibility Service 或其他 App 交互
- **Agent 模式**: 不仅执行函数，还能自主规划复杂任务（如"帮我订一张周末去上海的机票"）
- **本地 AI**: 端侧模型运行，无需联网
- **插件生态**: 开发者社区上传 DEX 插件，像 VSCode 插件市场一样
- **GUI 生成**: LLM 动态生成 Compose UI，不局限于函数调用
