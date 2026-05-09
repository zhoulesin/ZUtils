# ZOffice — 执行计划

> 基于当前状态：P0-P2 已完成，核心办公流程已打通

---

## 一、当前状态盘点

### 已完成

| 阶段 | 内容 | 文件 |
|------|------|------|
| 项目基础设施 | Gradle 构建、Kotlin + Compose + Material 3、AGP 9.1.1 | — |
| 共享引擎 | `application-shell` 引导、Engine、Workflow、DexLoader、Room、AutomationEngine | `:application-shell` / [ARCHITECTURE.md](../ARCHITECTURE.md) |
| **P0: 通讯日历** | `makePhoneCall`、`sendSms`、`queryCalendarEvents`、注册 + agent 路由 | 3 个新 Function + bootstrap + AgentExecution |
| **P1: 信息查询** | `base64` 注册、天气/翻译 MCP 对接、MCP 自动路由 | bootstrap + AgentExecution |
| **P2: LLM 全链路** | Server `LlmController`（`/api/v1/llm/parse` + `/llm/chat`）、Android `ServerLlmClient` 对接完成 | `LlmController.java` |
| Office UI | 底部 3 Tab（助理/自动化/我的）、Material 3 主题 | `OfficeRootScreen.kt` |

### 注册函数（16 个本地 + 1 个自动化）

| 模块 | 函数 |
|------|------|
| `:local-functions` | `getDeviceInfo`, `getScreenInfo`, `getStorageInfo`, `getNetworkType`, `getClipboard`, `setClipboard`, `base64`, `readFile`, `writeFile`, `shareFile`, `send_notification`, `readSms`, `queryContacts`, `makePhoneCall`, `sendSms`, `createCalendarEvent`, `queryCalendarEvents` |
| `:application-shell` | `create_automation` |

### MCP 工具（8 个，Server 端）

`weather_current`, `translate_text`, `news_headlines`, `geo_location`, `qrcode_generate`, `web_search`, `email_send`, `document_summarize`

### 执行路径

| 路径 | 说明 |
|------|------|
| **LLM parseIntent** | Server 返回完整 `Workflow`，客户端线性执行（MCP/Local/DEX 均注册为 `ZFunction`，统一调度） |
| **MCP** | 注册为 `McpFunction`，通过 FunctionRegistry 与 Local/DEX 同级调度 |

---

## 二、后续任务

### A — 助理体验提升

| # | 任务 | 说明 | 估计 |
|---|------|------|------|
| A1 | 会话管理 | 多会话 + 消息分组 + Room 持久化 | 2 天 |
| A2 | 结果卡片丰富 | 日历/天气/数据等按类型差异化展示 | 1 天 |
| A3 | 语音输入 | `SpeechRecognizer` 麦克风输入 | 1 天 |
| A4 | TTS 播报 | `TextToSpeech` 结果语音播报 | 1 天 |

### B — 自动化 Tab 补齐

| # | 任务 | 说明 | 估计 |
|---|------|------|------|
| B1 | 自动化列表 | 空状态引导 + Room 规则列表 + 开关/删除 | 1 天 |
| B2 | 创建规则 | 选择触发类型 + 时间/位置 + 执行动作 | 2 天 |
| B3 | 规则链路 | "每天早上8点播报天气" → LLM → 自动创建规则 | 1 天 |

### C — 插件市场

| # | 任务 | 说明 | 估计 |
|---|------|------|------|
| C1 | 插件浏览 | ZUtils Server 插件列表展示 | 1 天 |
| C2 | 安装/卸载 | DEX 下载 + DexLoader 注册 + 卸载 | 1 天 |

### D — 复杂场景增强

| # | 任务 | 说明 | 估计 |
|---|------|------|------|
| D1 | 图表生成 | LLM 输出数据 → 生成图表图片 | 2 天 |
| D2 | 微信自动发送 | AccessibilityService 自动操作微信 | 3-5 天 |
| D3 | calculate DEX 插件 | 独立模块构建为 DEX 插件 | 1 天 |

---

## 三、技术债务

| 项 | 说明 | 建议时机 |
|----|------|---------|
| `android.disallowKotlinSourceSets=false` | KSP 兼容临时方案 | A 后 |
| LLM API Key 明文存储 | 应改用 `EncryptedSharedPreferences` | A 后 |
| `office-app` 无单元测试 | 至少为 Calendar/MakePhoneCall 加测试 | B 间 |
| `ApplicationShell` 部分逻辑耦合 | API Key 存储逻辑被 `:app` 和 `:office-app` 复用 | D 后 |

---

## 四、建议执行顺序

```
优先 A（助理体验）→ B（自动化）→ C（插件）→ D（复杂增强）

A1-A2: 会话管理 + 结果卡片     → 提升日常使用体验
B1-B3: 自动化 Tab 补齐         → 三个 Tab 均非空壳
C1-C2: 插件市场                → 能力可扩展
D1-D3: 图表/微信/DEX 插件     → 端到端自动化
```

从 A 开始？还是先做其他？
