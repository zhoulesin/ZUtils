# engine-core — 引擎核心模块

engine-core 是 ZUtils 引擎的**唯一核心依赖**，包含接口定义、数据模型和服务编排引擎。
所有 ZFunction 插件（内置或 DEX）都依赖此模块。

## 模块依赖

```kotlin
dependencies {
    implementation(libs.kotlinx.serialization.json)   // @Serializable, JsonElement
    implementation(libs.androidx.core.ktx)           // ContextCompat
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
```

## 包结构

```
com.zhoulesin.zutils.engine/
├── Engine.kt                          # 顶层入口
├── core/                              # 9 个核心文件
│   ├── ZFunction.kt                   # 函数接口 + requiredDependencies
│   ├── ZResult.kt                     # 执行结果 + MediaType
│   ├── FunctionInfo.kt                # 函数元数据
│   ├── FunctionRegistry.kt            # 注册中心接口
│   ├── FunctionSource.kt              # 函数来源枚举
│   ├── Parameter.kt                   # 参数定义
│   ├── ExecutionContext.kt            # 执行上下文
│   ├── PermissionCheck.kt             # 权限检查结果
│   └── PermissionChecker.kt           # 权限检查器
├── workflow/                          # 工作流引擎
│   ├── Workflow.kt + WorkflowResult.kt# 数据模型
│   ├── WorkflowEngine.kt              # 接口
│   └── DefaultWorkflowEngine.kt       # 顺序执行实现
├── dex/                               # 动态加载接口
│   ├── DexLoader.kt                   # 加载接口
│   └── DexSpec.kt                     # DEX 规格 + DependencySpec
└── llm/
    └── LlmClient.kt                   # LLM 对接接口
```

## 核心接口

### ZFunction

```kotlin
interface ZFunction {
    val info: FunctionInfo
    val requiredDependencies: Map<String, String>  // name → version
    suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult
}
```

### Engine

```kotlin
class Engine(
    androidContext: Context,
    registry: FunctionRegistry = DefaultFunctionRegistry(),
    workflowEngine: WorkflowEngine = DefaultWorkflowEngine(),
    dexLoader: DexLoader? = null,
    llmClient: LlmClient? = null,
) {
    suspend fun execute(workflow: Workflow): WorkflowResult
}
```

`Engine.execute()` 自动执行 `resolveMissingFunctions()`：遍历工作流步骤，若函数不在 registry 中，调用 `DexLoader` 尝试加载并注册，同时校验依赖版本一致性。

### ZResult + MediaType

```kotlin
sealed interface ZResult {
    data class Success(data: JsonElement, mediaType: MediaType = TEXT)
    data class Error(message: String, code: String? = null, recoverable: Boolean = false)
}
```

## 使用方式

### 添加依赖

```kotlin
dependencies {
    implementation(project(":engine-core"))
}
```

### 实现函数

```kotlin
class MyFun : ZFunction {
    override val info = FunctionInfo(name = "myFun", description = "...")
    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult {
        return ZResult.ok("hello")
    }
}
```

### 注册并执行

```kotlin
val engine = Engine(androidContext)
engine.registry.register(MyFun())
val result = engine.execute(Workflow(steps = listOf(
    WorkflowStep(function = "myFun")
)))
```

## 模块依赖关系

```
engine-core  ←  plugin-manager  ←  app
                (DEX 加载实现)     (UI + 内置函数)
                  ↑
               TestFun (DEX 插件源码)
```

## DEX 生成

DEX 插件由 `TestFun` 模块编译。生成 DEX 的脚本见 `scripts/build-dex.sh`，自动输出到 `plugin-manager/src/main/assets/dex/`。
