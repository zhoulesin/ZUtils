# LLM 对接方案

## 现状

`LlmClient` 接口已定义但无实现。目前用户输入走的是 `parseQuery()` 关键词匹配，LLM 对接后由 LLM 理解用户意图并生成工作流。

```kotlin
interface LlmClient {
    suspend fun parseIntent(input: String, functions: List<FunctionInfo>): Workflow
    suspend fun summarize(input: String, result: WorkflowResult): String
}
```

## 架构

```
用户输入 → LlmClient.parseIntent()
              ↓
         HTTP POST → LLM API (OpenAI / Claude / 本地)
              ↓
        解析 response → Workflow
              ↓
        Engine.execute(workflow)
              ↓
        LlmClient.summarize() → 展示结果
```

### 通信协议

统一使用 **OpenAI Chat Completions API** 格式（事实标准，几乎所有模型兼容）。

**请求体**：
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "你是 ZUtils 的 AI 助手... 可用函数列表..."},
    {"role": "user", "content": "帮我查一下电池电量"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "getBatteryLevel",
        "description": "...",
        "parameters": { ... }
      }
    }
  ],
  "tool_choice": "auto"
}
```

**响应体**：
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [
        {
          "function": {
            "name": "getBatteryLevel",
            "arguments": "{}"
          }
        },
        {
          "function": {
            "name": "toast",
            "arguments": "{\"message\": \"当前电量85%\"}"
          }
        }
      ]
    }
  }]
}
```

### LLM 要求

- 支持 Function Calling / Tool Use
- 响应格式为 JSON
- 支持系统提示词（System Prompt）

### 最小模型要求

| 能力 | 最低要求 |
|------|---------|
| Function Calling | ✅ 必须支持 |
| 上下文长度 | ≥ 8K tokens |
| 响应速度 | ≤ 5s（首 token） |
| 中文理解 | ✅ |

---

## 方案一：单模型（固定 API）

最适合 MVP，快速跑通闭环。

### 配置

```kotlin
class SingleModelClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4o-mini",
) : LlmClient
```

配置可以是编译时常量，也可以通过 App 设置页面填写。

### Function Calling / Tool Use 是什么

**Function Calling**（OpenAI 叫法）和 **Tool Use**（Claude 叫法）是同一个概念：

> LLM 不直接生成最终文本，而是返回一个结构化的 JSON，表示「我想调用这个函数，参数是这些」。App 收到后执行函数，把结果返回给 LLM，LLM 再用自然语言总结。

```
用户：帮我查电量
  → LLM 返回: {"name": "getBatteryLevel", "arguments": "{}"}
  → App 执行 getBatteryLevel() → 85
  → 把结果 85 送回 LLM
  → LLM 总结: "当前电量 85%"
```

**没有 Function Calling 的模型**只能做纯文本对话，无法精确控制输出格式，解析函数调用需要靠正则或 Prompt 工程，不稳定。

### 各模型 FC 支持情况

| Provider | 模型 | 支持 FC | 备注 |
|----------|------|---------|------|
| OpenAI | gpt-4o-mini / gpt-4o | ✅ | 原生支持 `tools` 参数 |
| Claude (API2D 等) | claude-3.5-sonnet | ✅ | 叫 Tool Use，格式不同 |
| DeepSeek | deepseek-chat | ✅ | 兼容 OpenAI 格式 |
| **火山引擎** | doubao-pro / doubao-lite | ✅ | 兼容 OpenAI 格式 |
| **通义千问** | qwen-plus / qwen-max | ✅ | 兼容 OpenAI 格式 |
| **百度千帆** | ERNIE-4.0 / ERNIE-3.5 | ⚠️ 有限 | 只支持单轮 FC，不支持多 step |
| **腾讯混元** | hunyuan-pro | ⚠️ 有限 | 需特定 prompt |
| **GLM (智谱)** | glm-4-plus | ✅ | 兼容 OpenAI 格式 |
| Ollama 本地 | qwen2.5 | ✅ | 通过 `template` 支持 |
| Ollama 本地 | llama3.1 | ✅ | 原生支持 |
| Ollama 本地 | deepseek-r1 | ❌ | 推理模型，不支持 tool |

> 注意：百度千帆和腾讯混元的 FC 支持有限或需特殊参数。**方案中建议优先选择 FC 兼容度高的模型**（OpenAI / DeepSeek / 通义千问 / 火山引擎 / GLM），降低集成难度。

### 聚合平台

聚合平台统一管理多个模型的 API Key 和路由，App 只需对接一个地址。

| 平台 | baseUrl | 说明 |
|------|---------|------|
| **One API** | `http://{self-host}/v1` | 开源，自部署，支持几十个模型 |
| **New API** | `http://{self-host}/v1` | One API 的 Go 重写版，性能更好 |
| **API2D** | `https://api2d.com/v1` | 聚合转发，支持 OpenAI / Claude / Gemini |
| **OhMyGPT** | `https://api.ohmygpt.com/v1` | 国内可用，支持 GPT-4 / Claude |
| **AiHubMix** | `https://aihubmix.com/v1` | 聚合平台，支持 100+ 模型 |

> 聚合平台的接入方式与 OpenAI API 完全一致，只需修改 `baseUrl` 和 `apiKey`，无需额外适配。

---

## 方案二：多模型（用户可切换）

### 模型管理器

```kotlin
class ModelManager(context: Context) {
    private val prefs = context.getSharedPreferences("llm_config", 0)

    val currentProvider: String get() = prefs.getString("provider", "openai") ?: "openai"
    val currentModel: String get() = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
    val apiKey: String get() = prefs.getString("api_key", "") ?: ""

    fun switchProvider(provider: String, model: String, key: String) { ... }
    fun createClient(): LlmClient { ... }
}
```

### UI 配置页

```
┌─ 模型设置 ──────────────────────────┐
│ Provider: [OpenAI ▼]                │
│  ├─ OpenAI                          │
│  ├─ 火山引擎                        │
│  ├─ DeepSeek                        │
│  ├─ 通义千问                        │
│  ├─ 百度千帆                        │
│  ├─ 腾讯混元                        │
│  ├─ GLM (智谱)                      │
│  ├─ 聚合平台 (One API)             │
│  ├─ Ollama (本地)                   │
│  └─ 自定义...                       │
│                                     │
│ Model:   [gpt-4o-mini ▼]           │
│ API Key: [••••••••••••••••]        │
│ Base URL: [https://api.openai.com] │  ← 自动填充，也可手动改
└────────────────────────────────────┘
```

选择 Provider 后自动填充默认 `baseUrl` 和推荐 `model`，用户可手动修改。选择「自定义」时全部字段手动填写。聚合平台选择后 `baseUrl` 自动设为对应地址，`model` 留空让用户填写模型名。

---

## 关键设计决策

### 1. 函数描述生成

`FunctionInfo → JSON Schema` 的转换逻辑：

```kotlin
fun FunctionInfo.toToolSchema(): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                parameters.forEach { param ->
                    putJsonObject(param.name) {
                        put("type", param.type.toJsonType())
                        put("description", param.description)
                    }
                }
            }
            put("required", JsonArray(parameters.filter { it.required }.map { JsonPrimitive(it.name) }))
        }
    }
}
```

### 2. 多步骤工作流

LLM 可能在一次响应中返回多个 `tool_calls`（多步骤），也可能需要多轮对话协作完成复杂任务。

**策略**：按顺序执行所有 `tool_calls`，如果某个步骤出错，将错误信息返回给 LLM 请求修正。

### 3. 流式响应

可选。使用 SSE (Server-Sent Events) 实现逐 token 显示。MVP 阶段可选非流式（`stream: false`）。

### 4. 错误处理

| 错误 | 处理 |
|------|------|
| 网络超时 | 重试 2 次，间隔 1s |
| API Key 无效 | 提示用户检查配置 |
| Rate Limit | 等待后重试 |
| 模型不支持 FC | 降级到文本解析（正则提取函数名） |
| JSON 解析失败 | 重新请求（`response_format: { type: "json_object" }`） |

### 5. 安全

- API Key 存储：`EncryptedSharedPreferences`
- 不在日志中打印 API Key
- 本地模型（Ollama）不产生外部网络请求

---

## System Prompt 设计

```kotlin
val SYSTEM_PROMPT = """你是 ZUtils 的 AI 助手。ZUtils 是一个 Android 功能引擎。
你可以根据用户需求，通过工具调用来执行手机功能。

可用函数如下：
{{functions}}

工作流规则：
1. 将用户需求拆解为有序的函数调用链
2. 每个步骤的入参根据用户输入提取
3. 多步骤时返回多个 tool_calls，按执行顺序排列
4. 如果某个函数不需要额外参数，传入空对象 {}
5. 结果汇总后调用 toast 展示给用户

示例：
用户：帮我查电池电量再设亮度到50%
→ getBatteryLevel() → setScreenBrightness(level: 50) → toast(message: "电量85%，亮度已设为50%")
"""
```

---

## 实施路线

| Phase | 内容 | 预估 |
|-------|------|------|
| P0 | `SingleModelClient` 实现 + 设置页面 + 替换 `parseQuery` | 2-3 天 |
| P1 | 多模型支持 + `ModelManager` + Provider 管理 | 1-2 天 |
| P2 | 流式输出 + 错误修正对话 + 本地 Ollama 支持 | 2-3 天 |

---

## 文件结构

```
plugin-manager/src/main/java/com/zhoulesin/zutils/llm/
├── LlmClient.kt                    ← 接口（已存在 engine-core）
├── SingleModelClient.kt            ← 单模型实现
├── FunctionSchemaBuilder.kt        ← FunctionInfo → JSON Schema
├── ModelManager.kt                 ← 多模型管理
└── providers.json                  ← 预置 Provider 列表

app/src/main/java/com/zhoulesin/zutils/ui/screen/
└── LlmSettingsScreen.kt            ← 模型设置页面
```
