# ZUtils — AI 驱动的 Android 动态功能引擎

自然语言 → AI 拆解 → 执行（本地函数 / MCP 远程工具 / DEX 热加载插件）

---

## 项目定位

用户用自然语言描述需求，AI 拆解为工作链（Workflow），顺序调用函数执行。步骤间通过 pipeline 机制传递数据。若函数缺失，通过 DEX 动态加载补全。

```
用户需求 → LLM 两轮解析 → Workflow（含 pipeline） → 顺序调用 → 返回结果
                    Round1: 选函数                        ↓ (函数缺失)
                    Round2: 填参数                   DEX 热加载 → 继续执行
```

## 模块架构

扁平多模块 Gradle 布局：

```
engine-core                          ← 接口 + 数据模型 (ZFunction, Engine, WorkflowEngine, LlmClient…)
  ↑
application-shell                   ← 共享宿主（函数注册、Room、Agent、AutomationEngine）
  ├── app                            ← ZUtils 能力演示壳（Raycast 风格 Compose UI）
  ├── office-app                     ← ZOffice 产品壳（独立主题与界面）
  ├── plugin-manager                 ← DEX 加载 + manifest + 签名校验
  ├── mcp-manager                    ← MCP 远程工具调用（OkHttp + McpClient）
  ├── llm-manager                    ← LLM 客户端（ServerLlmClient / VolcengineLlmClient）
  ├── permissions                    ← 权限检查
  ├── ui-automation                  ← UI 自动化（AccessibilityService）
  └── functions/*                    ← 独立函数模块（calculate / time / uuid / weather）
```

依赖约定：`:app`、`:office-app` → `implementation(project(":application-shell"))`。新垂直产品在根下新建 `*-app/` 模块即可。

## 三层执行体系

| 类型 | 执行方式 | 示例 |
|------|---------|------|
| **local** | Engine → FunctionRegistry 直接执行 | `setClipboard`, `send_notification` |
| **mcp** | McpClient.callTool() → 服务器 MCP 执行 | `weather_current`, `translate_text` |
| **tool** | DexLoader 下载 DEX → 签名校验 → 注册 → 执行 | 市场插件（calculate 等） |

## LLM 两轮解析

Server 端 `parseIntent` 采用两轮 LLM 调用，降低 token 消耗并提升准确性：

```
Round 1（意图分类）              Round 2（参数填充）
  输入: 用户原始输入               输入: 用户原始输入
  Prompt: 函数名+一句话描述        Prompt: 选中函数的完整参数 schema
  输出: JSON（选了哪些函数）       输出: JSON（每个函数的完整参数）
  Token: ~400                     Token: ~800
```

Round 1 失败时自动回退到单轮模式（全部函数 + tool_calls）。

## Pipeline 步骤间数据传递

步骤间通过 `pipeline` 字段传递数据，由 `PipelineResolver` 在执行时解析：

```json
{
  "steps": [
    {"function": "news_headlines", "args": {"category": "科技"}},
    {"function": "translate_text", "args": {"target_lang": "zh"},
     "pipeline": {"text": "{0.result}"}}
  ]
}
```

- `{0.result}` — 引用 step 0 的完整执行结果
- `{0.result.title}` — 导航到 JSON 子字段
- `PipelineResolver` 在执行前将表达式解析为实际值，合并到 step 的 args 中

Server 端通过 `ParamDef.pipelineSource` 声明哪些参数可接受上游结果（如 `"previous_step_result"`），自动生成 pipeline 映射。

## 核心引擎（engine-core）

`engine-core` 是所有模块的唯一下游依赖，包含接口定义、数据模型和执行引擎。

### 包结构

```
com.zhoulesin.zutils.engine/
├── Engine.kt                    # 顶层 Facade
├── core/                        # 9 个核心接口
│   ├── ZFunction.kt             # 函数接口
│   ├── ZResult.kt               # 执行结果 Success / Error
│   ├── FunctionInfo.kt          # 函数元数据
│   ├── FunctionRegistry.kt      # 注册中心接口
│   ├── FunctionSource.kt        # BUILT_IN / DEX / REMOTE
│   ├── Parameter.kt             # 参数定义
│   ├── ExecutionContext.kt      # 执行上下文
│   ├── PermissionCheck.kt       # 权限检查结果
│   └── PermissionChecker.kt     # 权限检查器
├── workflow/                    # 工作流引擎
│   ├── Workflow.kt              # Workflow + WorkflowStep（含 pipeline 字段）
│   ├── WorkflowResult.kt        # 执行结果
│   ├── WorkflowEngine.kt        # 接口
│   ├── DefaultWorkflowEngine.kt # 顺序执行 + pipeline 数据注入
│   └── PipelineResolver.kt     # 解析 {N.result} 表达式
├── dex/                         # 动态加载接口
│   ├── DexLoader.kt             # 加载接口
│   └── DexSpec.kt               # DEX 规格 + DependencySpec
└── llm/
    └── LlmClient.kt             # LLM 对接接口
```

### 核心接口

```kotlin
interface ZFunction {
    val info: FunctionInfo
    val requiredDependencies: Map<String, String>  // name → version
    suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult
}

sealed interface ZResult {
    data class Success(data: JsonElement, mediaType: MediaType = TEXT)
    data class Error(message: String, code: String? = null, recoverable: Boolean = false)
}

class Engine(androidContext, registry, workflowEngine, dexLoader?, llmClient?) {
    suspend fun execute(workflow: Workflow): WorkflowResult
    // 自动 resolveMissingFunctions(): 遍历步骤，缺失函数触发 DexLoader 加载
}
```

### 实现并注册函数

```kotlin
class MyFun : ZFunction {
    override val info = FunctionInfo(name = "myFun", description = "...")
    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return ZResult.ok("hello")
    }
}

val engine = Engine(androidContext)
engine.registry.register(MyFun())
val result = engine.execute(Workflow(steps = listOf(
    WorkflowStep(function = "myFun")
)))
```

## 插件管理器（plugin-manager）

负责 DEX 文件加载、版本管理和依赖解析。

### dex_manifest.json

```
src/main/assets/dex/
├── dex_manifest.json           # 版本映射表
├── plugin_qrcode_v1.0.0.dex   # 插件 DEX（文件名含版本号）
└── lib_zxing_v3.5.3.dex       # 依赖 DEX
```

```json
{
  "plugins": [{
    "functionName": "generateQRCode",
    "version": "1.0.0",
    "dexUrl": "dex/plugin_qrcode_v1.0.0.dex",
    "className": "com.zhoulesin.zutils.testfun.QRCodeFunction",
    "dependencies": [
      { "name": "zxing-core", "version": "3.5.3", "dexUrl": "dex/lib_zxing_v3.5.3.dex" }
    ]
  }]
}
```

### DEX 加载流程

```
1. Engine.resolveMissingFunctions() 发现函数缺失
2. DefaultDexLoader.resolve() → 查询 dex_manifest.json
3. DefaultDexLoader.download() → assets (dev) 或远程 URL
4. DexVerifier.verify() → SHA-256 + RSA 签名校验
5. InMemoryDexClassLoader / DexClassLoader 加载
6. DexFunctionAdapter 包装为 ZFunction
7. registry.register() → 执行
8. PluginInstallRepo 持久化（下次启动自动加载）
```

### 构建命令

```bash
./scripts/build-dex.sh    # 编译 → DEX → 输出到 assets/dex/
```

## 技术栈

| 项 |  |
|-----|------|
| 语言 | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| 持久化 | Room + WorkManager |
| DEX 加载 | InMemoryDexClassLoader + SHA-256 + RSA |
| LLM | 火山引擎方舟（OpenAI 兼容）/ 自定义 |
| MCP | OkHttp + McpClient |
| 最低 SDK | Android 7.0 (API 26) |

## 快速开始

```bash
# Android Studio 打开项目，Run 即可

# DEX 插件构建（修改函数后）
./scripts/build-dex.sh
```

## 文档索引

- [架构设计](ARCHITECTURE.md) — 分层架构、模块职责、数据流、安全设计
- [能力矩阵](CAPABILITY.md) — 全部函数、MCP 工具、可用流程
- [PRD](PRD.md) — 产品需求与 Roadmap（`docs/product/PRD.md`）
- [DESIGN](DESIGN.md) — Raycast 风格设计规范（`docs/ui/DESIGN.md`）
- [office-app 执行计划](office-app/PLAN.md)
- 跨端日期约定 → `docs/contracts/zutils-datetime-strings.md`
