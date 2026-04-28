# PRD: ZUtils - 实用型 AI 手机助手

## 1. 产品概述

### 1.1 愿景
打造一个真正解决用户日常问题的 AI 手机助手。用户用自然语言描述需求，App 理解意图、调用实用服务、执行自动化任务，成为用户离不开的日常工具。

### 1.2 核心理念
```
用户需求 → LLM 理解 → Skill 匹配 → Skill 展开为 Workflow
                                    ↓
                      Workflow 调用 Function/MCP Tool
                                    ↓
                              结果处理 → 展示/TTS/通知
```

### 1.3 产品定位
一个"越用越好用"的 Android 助手——开箱即用解决高频问题，通过 DEX 插件扩展本地能力，通过 MCP 扩展云端能力，通过自动化让手机更智能。

---

## 2. 用户故事

### P0 (MVP — 2周)

| ID | 用户说 | 底层执行链路 |
|----|--------|------------|
| US-01 | "北京今天天气怎么样" | LLM→Skill(天气查询)→Workflow→MCP Tool(天气API)→展示 |
| US-02 | "查快递，单号 SF123456" | LLM→Skill(快递跟踪)→Workflow→MCP Tool(快递API)→展示 |
| US-03 | "读一下最近的短信" | LLM→Skill(短信播报)→Workflow→Function(读取短信)+LLM→TTS |
| US-04 | "翻译 hello 成中文" | LLM→Skill(翻译)→Workflow→MCP Tool(翻译API)→展示 |
| US-05 | "计算 256*8" | LLM→Skill(计算)→Workflow→Function(本地计算)→展示 |

### P1 (增强 — 2周)

| ID | 用户说 | 底层执行链路 |
|----|--------|------------|
| US-06 | "早上好" | 定时器→Skill(早安播报)→Workflow→[MCP(天气)+Function(日程)]→TTS |
| US-07 | "给老婆发微信说我在路上" | LLM→Skill(微信助手)→Workflow→Function(无障碍)→发送 |
| US-08 | "到家自动开 WiFi" | 位置触发→Skill(到家模式)→Workflow→[Function(开WiFi)+Function(调音量)] |
| US-09 | "这条短信是不是诈骗" | LLM→Skill(短信分析)→Workflow→Function(读短信)+LLM分析→展示 |
| US-10 | "帮我总结这段文字" | LLM→Skill(文本处理)→Workflow→Function(读剪贴板)+LLM→展示 |

### P2 (生态 — 持续)

| ID | 用户说 | 底层执行链路 |
|----|--------|------------|
| US-11 | "今天有什么科技新闻" | LLM→Skill(新闻摘要)→Workflow→MCP Tool(RSS/新闻API)→摘要 |
| US-12 | "美元兑人民币多少" | LLM→Skill(汇率查询)→Workflow→MCP Tool(汇率API)→展示 |
| US-13 | "000001 股票怎么样" | LLM→Skill(股票查询)→Workflow→MCP Tool(股票API)→展示 |
| US-14 | "帮我改写这段话更专业" | LLM→Skill(文本处理)→Workflow→LLM改写→Function(写剪贴板) |
| US-15 | "创建自定义自动化规则" | 可视化编辑→保存→Automation Engine 接管 |

---

## 3. 架构

### 3.1 三层架构总览

```
┌────────────── 用户感知层 ──────────────┐
│                                        │
│  ┌──────────┐  ┌──────────┐  ┌──────┐ │
│  │ 对话输入  │  │ 技能市场  │  │ 自动化│ │
│  │文本/语音  │  │安装/管理  │  │规则  │ │
│  └──────────┘  └──────────┘  └──────┘ │
│                                        │
│  ┌─────────────────────────────────┐   │
│  │   Skill Layer (能力层)           │   │
│  │  每个 Skill = 元数据+编排描述+    │   │
│  │  所用 Function/Tool 清单         │   │
│  │  - 天气查询 Skill                │   │
│  │  - 早安播报 Skill                │   │
│  │  - 到家自动化 Skill              │   │
│  └─────────────────────────────────┘   │
├────────────── 编排层 ──────────────┤
│  │                                        │
│  │  ┌─────────────────────────────────┐  │
│  │  │ Orchestrator / Workflow Engine  │  │
│  │  │ 将 Skill 展开为执行计划          │  │
│  │  │ 顺序/条件/循环/并行            │  │
│  │  │ 依赖解析 + 结果传递            │  │
│  │  └─────────────────────────────────┘  │
│  │                                        │
│  │  ┌─────────────────────────────────┐  │
│  │  │ Intent Parser (LLM Client)      │  │
│  │  │ 用户输入 → Function/Tool 调用链  │  │
│  │  │ 或 → Skill 匹配                 │  │
│  │  └─────────────────────────────────┘  │
├────────── 执行层 ───────────┤
│  │                                        │
│  │  ┌──────────────┬─────────────────┐   │
│  │  │ Function (+  │ MCP Tool Client │   │
│  │  │ 本地函数)     │ (远程 API 调用)  │   │
│  │  ├──────────────┼─────────────────┤   │
│  │  │ 内置: 计算/   │ 内置: 天气/     │   │
│  │  │ 剪贴板/亮度   │ 快递/翻译 API   │   │
│  │  ├──────────────┤                 │   │
│  │  │ DEX 插件:    │ 用户/社区配置:   │   │
│  │  │ 蓝牙控制/    │ 股票/新闻 API   │   │
│  │  │ 闹钟设置/    │                 │   │
│  │  │ 无障碍操作   │                 │   │
│  │  └──────────────┴─────────────────┘   │
│  │                                        │
│  │  ┌──────────┬──────────┬──────────┐   │
│  │  │ 网络模块  │ 本地存储  │ 权限管理 │   │
│  │  │OkHttp    │Room/DS   │Android   │   │
│  │  └──────────┴──────────┴──────────┘   │
└────────────────────────────────────────────┘
```

### 3.2 各层定义

| 层 | 名称 | 职责 |
|----|------|------|
| **Layer 1** | **Function / MCP Tool** | 原子执行单元。Local Function 执行设备本地代码，MCP Tool 进行远程 API 调用。两者都用 JSON Schema 描述，LLM 可调用 |
| **Layer 2** | **Workflow / Orchestrator** | 编排 Function/Tool 的调用链。决定顺序/条件/循环/并行、结果传递、错误处理 |
| **Layer 3** | **Skill** | 面向用户的能力包。打包了所需的 Function/MCP Tool 集合 + 编排逻辑 + Prompt 提示。符合 MCP 社区 Skills 规范方向 (SEP-2076) |

### 3.3 核心链路：从用户输入到执行

```
用户: "北京天气"
  ↓
LLM Intent Parser
  ├─ (当前设计) 直接生成 Function/Tool 调用链
  │   → weather_current(location="北京")
  │   → tts_speak(text="北京今天晴...")
  │
  └─ (Skill 系统) 先匹配 Skill，再由 Skill 展开为 Workflow
      → 匹配 Skill: "weather-query"
      → Skill 展开:
          Step 1: [MCP Tool] weather_current(location="北京")
          Step 2: [Function]  tts_speak(text="$result")
  ↓
Workflow Engine 顺序执行
  ↓
结果返回 → UI 展示 / TTS 播报
```

**说明**：Skill 是可选的包装层。没有 Skill 系统时，LLM 直接生成 Function/Tool 调用链也能工作。Skill 的价值在于：预配置的执行逻辑更稳定、可复用、可分享。

---

## 4. 核心模块设计

### 4.1 Layer 1: Function（本地原子执行单元）

与原有 ZFunction 概念一致。执行设备本地代码，可以通过 DEX 动态加载扩展。

```kotlin
interface ZFunction {
    val name: String
    val description: String
    val parameters: List<Parameter>  // JSON Schema 格式
    val permissions: List<String>    // 需要的 Android 权限
    suspend fun execute(context: ExecutionContext, args: Map<String, Any?>): Any?
}
```

#### 内置 Function 清单

| Function | 描述 | 参数 | 类型 |
|----------|------|------|------|
| `calculate` | 数学计算 | `expression: string` | 内置 |
| `getCurrentTime` | 获取当前时间 | `format: string(可选)` | 内置 |
| `setScreenBrightness` | 设置屏幕亮度 | `level: number(0-100)` | 内置 |
| `getBatteryLevel` | 获取电池电量 | - | 内置 |
| `toast` | 显示 Toast | `message: string` | 内置 |
| `setVolume` | 设置媒体音量 | `level: number(0-100)` | 内置 |
| `setWiFiEnabled` | 开关 WiFi | `enabled: boolean` | 内置 |
| `setBluetoothEnabled` | 开关蓝牙 | `enabled: boolean` | 内置 |
| `readSms` | 读取短信 | `count: number(可选)` | 内置 |
| `setClipboard` | 设置剪贴板 | `text: string` | 内置 |
| `getClipboard` | 获取剪贴板 | - | 内置 |
| `setAlarm` | 设置闹钟 | `hour:number, minute:number` | DEX |
| `sendWeChatMessage` | 微信发消息 | `contact:string, text:string` | DEX |
| `openApp` | 打开应用 | `packageName: string` | DEX |

#### DEX 插件加载

```kotlin
interface DexLoader {
    fun resolve(functionName: String): DexSpec?
    suspend fun download(spec: DexSpec): File
    fun load(dexFile: File): List<ZFunction>
}
```

DEX 加载的是**本地执行代码**——需要在 Android 设备上运行的逻辑，如蓝牙控制、无障碍操作等。

### 4.2 Layer 1: MCP Tool（远程原子执行单元）

符合 MCP (Model Context Protocol) 规范的远程工具调用。由 MCP Server 提供，通过 HTTP 调用，不需要往手机上加载代码。

```kotlin
data class McpTool(
    val name: String,
    val title: String?,
    val description: String,
    val inputSchema: JsonObject,  // JSON Schema
    val serverUrl: String,        // MCP Server 地址
    val apiKey: String? = null    // 用户配置的 API Key
)

interface McpToolClient {
    // 发现 MCP Server 上的所有 Tool
    suspend fun listTools(serverUrl: String): List<McpTool>
    // 调用 MCP Tool
    suspend fun callTool(tool: McpTool, args: Map<String, Any?>): Any?
}
```

#### 内置 MCP Tool 清单

| MCP Tool | 描述 | 参数 | 后端 |
|----------|------|------|------|
| `weather_current` | 查询实时天气 | `location:string, units:string(可选)` | 和风天气 API |
| `weather_forecast` | 查询未来预报 | `location:string, days:number` | 和风天气 API |
| `express_track` | 快递跟踪 | `trackingNo:string, carrier:string(可选)` | 快递100 API |
| `translate_text` | 文本翻译 | `text:string, targetLang:string, sourceLang(可选)` | 翻译 API |

### 4.3 Layer 2: Workflow Engine（编排层）

**职责**：接收 Function/Tool 调用链，顺序/条件/循环执行，传递上下文，处理错误。

```kotlin
data class WorkflowStep(
    val stepId: String,
    val type: StepType,       // FUNCTION / MCP_TOOL / LLM / CONDITION
    val executor: String,     // Function name 或 MCP Tool name
    val args: Map<String, Any?>,
    val dependsOn: List<String> = emptyList(),
    val onError: ErrorHandler? = null
)

enum class StepType {
    FUNCTION,    // 调用本地 Function（含 DEX 插件）
    MCP_TOOL,   // 调用远程 MCP Tool
    LLM,         // 调用 LLM
    CONDITION,   // 条件判断
    WAIT         // 等待/延迟
}

data class WorkflowContext(
    val steps: List<WorkflowStep>,
    val results: MutableMap<String, Any?>,
    val errors: MutableMap<String, String>,
    var currentStep: Int = 0
)

interface WorkflowEngine {
    // 展开：Skill → WorkflowStep[]
    suspend fun expand(skill: Skill, params: Map<String, Any?>): List<WorkflowStep>
    // 执行：依次/按依赖执行所有步骤
    suspend fun execute(workflow: WorkflowContext): WorkflowResult
    // 单次执行：LLM 生成的调用链直接执行
    suspend fun executeSteps(steps: List<WorkflowStep>): WorkflowResult
}
```

**执行流程**：
```
1. 输入 WorkflowStep[]（来自 LLM 生成 或 Skill 展开）
2. 校验每个 step 的 executor 是否可用（Function Registry / MCP Tool Registry）
3. Function 缺失 → 触发 DexLoader 动态加载
4. MCP Tool 缺失 → 提示用户配置 API Key 或安装
5. 按依赖关系顺序执行，传递上下文
6. 全部完成 → 汇总结果，交给 LLM 生成总结（可选）
7. 返回给用户
```

### 4.4 Layer 3: Skill（面向用户的能力包）

Skill 是 MCP 社区正在标准化中的概念（SEP-2076, Skills Over MCP WG）。它打包了：

- 所需的 Function/MCP Tool 集合
- Workflow 编排描述（执行步骤和顺序）
- 示例话语和触发条件
- Prompt 提示词

```kotlin
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val examples: List<String>,         // 用户可能说的话
    val requiredTools: List<String>,    // 依赖的 Function/MCP Tool ID
    val workflow: SkillWorkflow,        // 预定义的执行步骤
    val permissions: List<String>,      // 需要的权限
    val triggerType: TriggerType,       // 触发方式
    val promptTemplate: String? = null  // LLM 提示词模板
)

enum class TriggerType {
    VOICE,      // 语音/文本触发
    LOCATION,   // 位置触发
    TIME,       // 时间触发
    EVENT,      // 事件触发
    MANUAL      // 手动触发
}

data class SkillWorkflow(
    val steps: List<SkillWorkflowStep>,
    val outputConfig: OutputConfig  // 结果如何展示/处理
)

data class SkillWorkflowStep(
    val stepId: String,
    val type: StepType,           // FUNCTION / MCP_TOOL / LLM
    val executor: String,         // 对应的 Function/MCP Tool name
    val argsTemplate: Map<String, String>,  // 参数模板，可引用上游结果
    val description: String       // 人类可读的步骤说明
)
```

#### Skill 示例：早安播报

```kotlin
Skill(
    id = "morning-briefing",
    name = "早安播报",
    description = "每天早上播报天气、日程和路况",
    examples = listOf("早上好", "早安", "今天有什么安排"),
    requiredTools = listOf(
        "weather_current",     // MCP Tool: 天气
        "getCalendarEvents",   // Function: 读取日历
        "tts_speak"            // Function: TTS 播报
    ),
    workflow = SkillWorkflow(
        steps = listOf(
            SkillWorkflowStep("get_weather", MCP_TOOL, "weather_current",
                argsTemplate = mapOf("location" to "{user.home}")),
            SkillWorkflowStep("get_schedule", FUNCTION, "getCalendarEvents",
                argsTemplate = mapOf("date" to "today")),
            SkillWorkflowStep("summary", LLM, "llm_summarize",
                argsTemplate = mapOf(
                    "prompt" to "将以下天气和日程信息总结为一段早安播报",
                    "weather" to "{get_weather.result}",
                    "schedule" to "{get_schedule.result}"
                )),
            SkillWorkflowStep("speak", FUNCTION, "tts_speak",
                argsTemplate = mapOf("text" to "{summary.result}"))
        )
    ),
    triggerType = TIME
)
```

#### Skill 来源

| 来源 | 说明 | 用户操作 |
|------|------|----------|
| **系统内置** | App 预装的 Skill（天气查询、计算器等） | 开箱即用 |
| **用户创建** | 用户选择 Function/Tool，编排成 Workflow，保存为 Skill | 可视化编辑器 |
| **社区分享** | 用户分享 Skill 配置（JSON 文件/二维码） | 导入即可用 |
| **DEX 插件附带** | 部分 DEX 插件可能附带 Skill 描述 | 安装插件后自动注册 |

### 4.5 Function Registry

管理所有可用的原子执行单元（本地 Function + MCP Tool）。

```kotlin
class FunctionRegistry {
    // 注册本地 Function
    fun registerFunction(function: ZFunction)
    // 注册 MCP Tool
    fun registerMcpTool(tool: McpTool)
    // 查找执行器
    fun resolve(name: String): Executor?
    // 获取所有可用执行器（给 LLM 生成注册列表）
    fun getAllExecutors(): List<ExecutorDescriptor>
}

sealed class Executor {
    data class Local(val function: ZFunction) : Executor()
    data class Remote(val tool: McpTool) : Executor()
}

data class ExecutorDescriptor(
    val name: String,
    val description: String,
    val parameters: JsonObject,   // JSON Schema
    val executorType: String      // "function" / "mcp_tool"
)
```

### 4.6 LLM Client

```kotlin
interface LlmClient {
    // 意图解析：用户输入 → Function/MCP Tool 调用链
    suspend fun parseIntent(
        userInput: String,
        availableExecutors: List<ExecutorDescriptor>
    ): List<WorkflowStep>

    // Skill 匹配：用户输入 → 匹配的 Skill
    suspend fun matchSkill(
        userInput: String,
        availableSkills: List<SkillDescriptor>
    ): SkillMatchResult?

    // LLM 通用调用（摘要/翻译/分析等）
    suspend fun chat(prompt: String, context: List<Message>): String
}
```

### 4.7 Automation Engine（自动化引擎）

**职责**：管理定时、位置、事件触发的自动化规则。

```kotlin
@Entity
data class AutomationRule(
    @PrimaryKey val id: String,
    val name: String,
    val skillId: String?,         // 可直接关联一个 Skill
    val trigger: TriggerConfig,
    val actions: List<ActionStep>, // 或直接定义动作列表
    val isEnabled: Boolean = true
)

sealed class TriggerConfig {
    data class LocationTrigger(val lat: Double, val lng: Double,
                               val radius: Int,
                               val enterOrExit: Boolean) : TriggerConfig()
    data class TimeTrigger(val hour: Int, val minute: Int,
                           val days: List<Int>) : TriggerConfig()
    data class EventTrigger(val eventType: String,
                            val condition: String) : TriggerConfig()
}

data class ActionStep(
    val executor: String,     // Function name 或 MCP Tool name
    val params: Map<String, Any?>,
    val delayMs: Long = 0
)
```

---

## 5. 典型执行链路示例

### 场景 1: 天气查询（LLM 直接生成调用链）

```
用户: "北京今天天气怎么样"
  ↓
LLM 解析（当前设计——没有 Skill 也 work）
  ↓
返回调用链:
  Step 1: [MCP Tool] weather_current(location="北京")
  Step 2: [Function]  tts_speak(text="$result")
  ↓
Workflow Engine 校验:
  - weather_current → MCP Tool Registry 找到 ✓
  - tts_speak → Function Registry 找到 ✓
  ↓
顺序执行:
  [1/2] weather_current() → "北京今天晴 23-28℃"
  [2/2] tts_speak("北京今天晴 23-28℃") → OK
  ↓
结果展示/TTS 播报
```

### 场景 2: 早安播报（通过 Skill 展开）

```
触发: 每日 7:30（定时触发器）
  ↓
匹配 Skill: "morning-briefing"
  ↓
Skill 展开为 Workflow:
  Step 1: [MCP Tool] weather_current(location="北京")
  Step 2: [Function]  getCalendarEvents(date="today")
  Step 3: [LLM]       将天气和日程总结为早安播报词
  Step 4: [Function]  tts_speak(text="早上好...")
  ↓
Workflow Engine 顺序执行，步骤间传递结果
  ↓
TTS 播报: "早上好！北京今天晴，23-28℃。
           您今天有 2 个会议..."
```

### 场景 3: 到家自动化（自动化规则）

```
触发: Geofencing 检测到达"家"位置
  ↓
查找 AutomationRule(trigger=LocationTrigger(home, enter))
  ↓
执行 ActionStep 列表:
  Step 1: [Function] setWiFiEnabled(true)
  Step 2: [Function] setVolume(level=50)
  Step 3: [Function] toast(message="已到家，WiFi 已开启")
  ↓
通知栏显示执行结果
```

---

## 6. 阶段性规划 (Roadmap)

### Phase 1: 基础设施（2 周）
- Function Registry 重构（支持 Local + MCP Tool 两种类型）
- ZFunction 接口保留，新增 MCP Tool 注册机制
- Workflow Engine 核心（顺序执行 + 依赖解析）
- LLM Client 保持现有 Function Calling 模式
- 对话 UI

### Phase 2: 实用 MCP Tool（2 周）
- 天气查询 MCP Tool（和风天气 API）
- 快递跟踪 MCP Tool（快递100 API）
- 文本翻译 MCP Tool（翻译 API）
- MCP Tool 配置页面（API Key 管理）
- 用户自建 MCP Server 的支持文档

### Phase 3: Skill 系统（2 周）
- Skill 定义与存储
- Skill → Workflow 展开机制
- 预设 Skill：早安播报、到家模式、睡前模式
- Skill 市场基础版（JSON 导入/导出）
- 自动化规则与 Skill 关联

### Phase 4: 增强与生态（2 周）
- DEX 插件体系保持 + 更多实用插件
- 自动化引擎完善（位置/时间/事件触发）
- 短信读取与分析
- 屏幕理解（AccessibilityService）
- 插件市场

### Phase 5: 持续
- 社区 Skill 分享
- MCP Tool 市场（用户可自由添加任意 MCP Server）
- 本地 LLM 支持
- 跨应用操作增强

---

## 7. 关键技术决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 本地原子执行 | `ZFunction` + `DexClassLoader` | 无需动原有架构 |
| 远程原子执行 | MCP 协议 (Model Context Protocol) | 业界标准，兼容性好 |
| 编排层 | Workflow Engine | 复用原有设计 |
| Skill 定义 | Kotlin Data Class + JSON | 类型安全，可序列化分享 |
| Skill 匹配 | LLM Function Calling + 关键词 | 灵活且准确 |
| 意图解析 | LLM 直接生成调用链（无 Skill）或 匹配 Skill | 渐进式设计，均可工作 |
| 自动化触发 | Geofencing + WorkManager + NotificationListener | 官方推荐方案 |
| 序列化 | Kotlinx Serialization | Kotlin 原生 |
| 自动化存储 | Room | 结构化存储 |

---

## 8. 各层对比

| 维度 | Function (Layer 1) | MCP Tool (Layer 1) | Workflow (Layer 2) | Skill (Layer 3) |
|------|--------------------|--------------------|--------------------|-----------------|
| **本质** | 本地代码执行 | 远程 API 调用 | 执行计划 | 能力包 |
| **如何扩展** | DEX 动态加载 | 添加 MCP Server 配置 | LLM 生成 / 预定义 | 创建/安装/分享 |
| **是否需要网络** | 不需要 | 需要 | 取决于步骤 | 取决于步骤 |
| **LLM 是否能直接调用** | 能 | 能 | 能（LLM 生成） | 能（匹配后展开） |
| **用户感知** | 无（底层执行单元） | 无（底层执行单元） | 无（中间编排） | 有（用户看到的"能力"） |
| **代码加载位置** | Android 设备本地 | 远端 Server | — | — |

---

## 9. 与现有架构的关系

| 现有能力 | 新架构中如何体现 |
|----------|----------------|
| 15 个内置函数 | 作为内置 ZFunction，保留不变 |
| DEX 动态加载 | 加载的是 ZFunction（本地执行代码），与现有一致 |
| Workflow Engine | 保留，作为 Layer 2 编排层 |
| LLM 集成 | 保持不变，LLM 可识别 Function + MCP Tool |
| 权限管理 | 保留，Skill 声明所需权限集合 |
| 插件市场 | 扩展为：DEX 插件 + MCP Tool 配置 + Skill 分享 |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| API 费用过高 | 服务不可用 | 提供免费额度 + 用户自备 API Key |
| LLM 输出不稳定 | 调用链生成失败 | 校验 + 重试 + 预定义 Skill 兜底 |
| MCP Server 不可用 | 远程功能不可用 | 缓存 + 降级提示 + 重试 |
| 短信权限隐私担忧 | 用户不信任 | 明确告知用途 + 本地处理不上传 |
| 位置触发耗电 | 电池续航差 | 使用 Geofencing API（低功耗） |
| DEX 安全风险 | 恶意代码执行 | 签名校验 + 权限声明 + 用户确认 |
| Android 版本兼容 | 功能不可用 | minSdk 24，充分测试主流版本 |

---

## 11. 非功能需求

- **冷启动到可用**: < 2 秒
- **LLM 意图解析**: < 3 秒
- **Function 执行**: < 1 秒（本地）
- **MCP Tool 调用**: < 5 秒（含网络）
- **DEX 加载**: < 3 秒（本地缓存）
- **位置触发延迟**: < 30 秒
- **定时任务偏差**: < 1 分钟
- **基础包大小**: < 15MB
- **支持 Android 7.0+**（minSdk = 24）
- **离线基本能力**: 内置 Function 无网可执行

---

## 12. 未来畅想

- **跨应用能力**: 通过 AccessibilityService 与任意 App 交互
- **Agent 模式**: 自主规划复杂任务（如"帮我订周末去上海的机票"）
- **本地 AI**: 端侧模型运行，完全离线可用
- **Skill 市场**: 像 VSCode 插件市场一样的 Skill 分享社区
- **MCP 生态**: 用户自由配置任意 MCP Server 扩展能力
