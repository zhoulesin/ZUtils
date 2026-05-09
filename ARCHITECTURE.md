# ZUtils Android — 架构设计

---

## 一、分层架构

```
┌─────────────────────────────────────────────────────┐
│                  Agent/Workflow Layer                  │
│  AgentExecution (Server LLM 单轮 Workflow)              │
│  AutomationEngine (WorkManager定时规则)               │
├─────────────────────────────────────────────────────┤
│                  Function Registry                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │  Local   │  │  MCP     │  │  DEX     │          │
│  │Functions │  │  Tools   │  │  Plugins │          │
│  │(模块)    │  │(模块)    │  │(模块)    │          │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘          │
├───────┼─────────────┼──────────────┼────────────────┤
│       ▼             ▼              ▼                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ Registry │  │McpClient│  │ DexLoader│          │
│  │ 直执行    │  │ HTTP Server│  │验签→注册→执行 │
│  │          │  │          │  │ Bridge注入    │
│  └──────────┘  └──────────┘  └──────────┘          │
├─────────────────────────────────────────────────────┤
│               Engine Core (Facade)                    │
│  WorkflowEngine · DexLoader · LlmClient · Registry    │
│  PermissionChecker · ZFunction Interface              │
├─────────────────────────────────────────────────────┤
│               Data & Infrastructure                    │
│  Room (AutomationRule) · WorkManager · OkHttp         │
└─────────────────────────────────────────────────────┘
```

---

## 二、核心数据流

### 用户输入执行路径

```
用户输入 "北京今天天气"
  │
  └─ ServerLlmClient.parseIntent() → POST /api/v1/llm/parse
      → Server LlmService → 火山引擎豆包
      → 返回完整 Workflow: [weather_current, tts_speak]
      → Engine.execute() → 线性执行每步
      → LlmClient.summarize() → 展示结果
```

> 无客户端多轮循环，无关键词匹配。所有函数（MCP/Local/DEX）均注册为 ZFunction，统一调度。

### 三种执行方式详细链路

| 方式 | 链路 |
|------|------|
| **local** | `engine.registry.get(name)` → `checkPermissions()` → `function.execute(ctx, args)` |
| **mcp** | `McpClient.callTool(name, args)` → `POST /api/v1/mcp/call` → Server 执行 → 返回结果 |
| **tool/dex** | `resolveMissingFunctions()` → `DexLoader.resolve()+download()+load()` → 验签(SHA-256+RSA) → `registry.register()` → 执行 |

### 自动化链路

```
"每天早上8点发武汉天气"
  → create_automation(cron="0 8", steps=[weather_current+send_notification])
  → Room 保存 → WorkManager 注册定时 Worker
  → 每天 8:00: Worker 启动 → McpClient 查天气 → send_notification 通知
```

---

## 三、模块职责

### 3.1 engine-core — 核心引擎

| 包 | 关键文件 | 职责 |
|----|---------|------|
| `core/` | `ZFunction`, `FunctionInfo`, `FunctionRegistry`, `ExecutionContext`, `ZResult`, `Parameter` | 核心接口与数据模型（被 `:local-functions`、`:mcp-manager` 等模块依赖） |
| `workflow/` | `Workflow`, `WorkflowStep`, `WorkflowEngine`, `DefaultWorkflowEngine`, `PipelineResolver` | 工作流定义与执行 |
| `dex/` | `DexLoader`, `DexSpec`, `DexVerifier` | DEX 加载与签名验证接口 |
| `llm/` | `LlmClient`, `ChatMessage`, `ChatResult` | LLM 客户端接口 |
| `Engine.kt` | Engine Facade | 统一对外接口，持有 Registry + WorkflowEngine + DexLoader + LlmClient |

### 3.2 application-shell — 共享宿主

| 组件 | 职责 |
|------|------|
| `ZUtilsEngineBootstrap` | 引擎引导：注册 17 个本地函数（来自 `:local-functions`）+ MCP 函数（来自 `:mcp-manager`）+ 自动化函数、初始化自动化引擎、加载缓存的 DEX 插件 |
| `AgentExecution` | Agent：Server LLM parseIntent → Workflow → 线性执行 → 汇总，无多轮循环 |
| `AutomationEngine` | 自动化规则管理：Cron 解析、WorkManager 调度、步骤类型推断（mcp/tool/local） |
| `NotificationHelper` | → 已迁移至 `:local-functions` 模块 |
| `ServerConfig` | 服务器地址常量（`http://10.0.2.2:8080`） |
| `AgentModels` | `ResultContent`, `HistoryEntry`, `EntryType` |

### 3.2 local-functions — 本地函数

| 组件 | 职责 |
|------|------|
| `functions/*.kt` | 17 个内置本地函数的 `ZFunction` 实现（设备信息、文件、日历、通讯录、短信等） |
| `notification/NotificationHelper` | 系统通知工具 |

**注册的内置函数（16 个）：**

`getDeviceInfo`, `getScreenInfo`, `getStorageInfo`, `getNetworkType`, `getClipboard`, `setClipboard`, `base64`, `readFile`, `writeFile`, `shareFile`, `send_notification`, `readSms`, `queryContacts`, `makePhoneCall`, `sendSms`, `createCalendarEvent`, `queryCalendarEvents`

### 3.3 mcp-manager — MCP 客户端

| 组件 | 职责 |
|------|------|
| `McpClient` | HTTP 客户端，连接 `POST /api/v1/mcp/call` 和 `GET /api/v1/mcp/tools` |
| `McpFunction` | 实现 `ZFunction`，将 MCP 工具包装为本地可调用的函数（`execute()` 内调 `McpClient.callTool()`） |
| `McpKnownTools` | 遗留常量集，仅 AutomationEngine 用于 type 推断 |

**8 个 MCP 工具（Server 端实现）：**

`weather_current`(天气), `translate_text`(翻译), `news_headlines`(新闻), `geo_location`(定位), `qrcode_generate`(二维码), `web_search`(搜索), `email_send`(邮件), `document_summarize`(文档摘要)

### 3.4 plugin-manager — DEX 插件管理

| 组件 | 职责 |
|------|------|
| `DefaultDexLoader` | DEX 下载、SHA-256 验证、`InMemoryDexClassLoader` 加载 |
| `DexFunctionAdapter` | 将 DEX 中 `handle(String): String` 方法包装为 ZFunction |
| `BridgeDexAdapter` | 将 DEX 中 `execute(Map)` + `setApiBridge(ApiBridge)` 方法包装为 ZFunction |
| `bridge/AppApiBridge` | `ApiBridge` 实现：通过反射调用 Android API，供 DEX 插件调用系统能力 |
| `PluginInstallRepo` | 已安装插件的本地 DB 持久化 |
| `PluginStorage` | 插件文件存储 |

### 3.5 llm-manager — LLM 客户端

| 组件 | 用途 |
|------|------|
| `ServerLlmClient` | 调用 ZUtils Server `POST /api/v1/llm/parse` 和 `/api/v1/llm/chat` |
| `VolcengineLlmClient` | 直连火山引擎方舟 API（OpenAI 兼容）|

### 3.6 app — ZUtils 演示 UI

底部 3 Tab 导航：

| Tab | 内容 |
|-----|------|
| **EXECUTE** | 聊天式输入框 + Agent 执行 + 结果历史列表（关键词/LLM/DEX） |
| **CAPABILITIES** | 已注册函数列表 + MCP 工具 + DEX 插件 + Skills |
| **AUTOMATION** | 自动化规则列表 + 创建/开关/删除 |

### 3.7 office-app — ZOffice 产品壳

底部 3 Tab 导航（独立主题，不与 app 共享 Composable）：

| Tab | 内容 |
|-----|------|
| **助理** | 聊天式输入框 + Agent 执行 + 结果卡片 |
| **自动化** | 预留（定时与规则） |
| **我的** | 预留（App 信息） |

---

## 四、LLM 集成

### 架构选择

采用 **Server 端 LLM** 方案。Android 仅做一次性 `parseIntent` 获取完整 Workflow，然后线性执行，最后 `summarize` 结束。无客户端多轮循环。

```
Android AgentExecution → ServerLlmClient.parseIntent() → POST /api/v1/llm/parse
  → Server LlmService → 火山引擎方舟 /chat/completions
  → 返回完整 WorkflowStep[]
  → Engine.execute() 逐个执行（MCP/Local/DEX 均在 FunctionRegistry 中）
  → LlmClient.summarize() → 展示
```

### 双客户端

| 客户端 | 目标地址 | 适用场景 |
|--------|---------|---------|
| `ServerLlmClient` | `http://10.0.2.2:8080/api/v1/llm/*` | 开发/生产（Server 代理 LLM，可共享函数池） |
| `VolcengineLlmClient` | `https://ark.cn-beijing.volces.com` | 直连火山（API Key 配置在端上） |

### 单次执行

`AgentExecution.runQuery()` 执行流程：
1. `ServerLlmClient.parseIntent()` → 从 Server 获取完整 `Workflow`
2. `Engine.execute(Workflow)` → 线性执行全部步骤
3. `ServerLlmClient.summarize()` → 获取执行摘要

> 不再需要多轮循环。MCP 工具已注册为 `McpFunction`，与 Local/DEX 函数同等待遇。

---

## 五、安全设计

| 维度 | 实现 |
|------|------|
| DEX 完整性 | SHA-256 校验 + RSA 签名验证 |
| 代码隔离 | 独立 ClassLoader 加载插件 |
| 权限前置 | 每个 Function 声明 permissions，执行前 `PermissionChecker` 检查 |
| 权限 UI | 未授权时显示「去授权」按钮 → 系统设置 |
| LLM API Key | 存 SharedPreferences（待迁移 EncryptedSharedPreferences） |

---

## 六、设计模式

| 模式 | 应用 |
|------|------|
| Facade | `Engine` 统一对外接口 |
| Registry | `FunctionRegistry` 管理函数注册与查找 |
| Strategy | `WorkflowEngine` 接口 + `DefaultWorkflowEngine` 实现 |
| Chain of Responsibility | Workflow 步骤顺序执行，中断即停 |
| Template Method | `BaseDexLoader` 定义加载骨架 |
| Plugin/Dynamic Loading | DEX 动态加载 |
| Tool Calling (LLM) | LLM 返回结构化函数调用 → 引擎执行 → 回传结果 |

---

## 七、插件（DEX）加载流程

```
1. Engine.resolveMissingFunctions() 发现函数缺失
2. DefaultDexLoader.resolve() → 查询 dex_manifest.json
3. DefaultDexLoader.download() → 从 assets (dev) 或远程 URL 下载 DEX
4. DexVerifier.verify() → SHA-256 完整性校验 + RSA 签名验证
5. InMemoryDexClassLoader / DexClassLoader 加载
6. DexFunctionAdapter 包装为 ZFunction
7. registry.register() → 执行 → 返回结果
8. PluginInstallRepo 持久化（下次启动自动加载）
```

### dex_manifest.json 格式

```json
{
  "version": 2,
  "plugins": {
    "generateQRCode": {
      "version": "1.0.0",
      "dexUrl": "dex/plugin_qrcode_v1.0.0.dex",
      "className": "com.zhoulesin.testfun.QRCodeFunction",
      "dependencies": ["zxing"]
    },
    "zxing": {
      "version": "3.5.3",
      "dexUrl": "dex/lib_zxing_v3.5.3.dex"
    }
  }
}
```

### 构建命令

```bash
./scripts/build-dex.sh    # 一键编译 TestFun → DEX → 输出到 assets/dex/
```
