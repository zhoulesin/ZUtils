# ZUtils Android — 能力矩阵

---

## 一、已注册函数（17 个，application-shell）

### 设备信息

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `getDeviceInfo` | 品牌/型号/OS 版本 | 无 | 无 |
| `getScreenInfo` | 分辨率/密度/刷新率 | 无 | 无 |
| `getStorageInfo` | 存储总容量/已用/可用 | 无 | 无 |
| `getNetworkType` | WiFi/移动/以太网 | 无 | `ACCESS_NETWORK_STATE` |

### 工具

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `getClipboard` | 读取剪贴板 | 无 | 无 |
| `setClipboard` | 写入剪贴板 | `text` | 无 |
| `base64` | Base64 编解码 | `action`(encode/decode), `text` | 无 |

### 文件

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `readFile` | 读取文本文件 | `path`, `encoding` | 无 |
| `writeFile` | 写入文本文件（自动建目录） | `content`, `path`, `filename` | 无 |
| `shareFile` | 系统分享面板 | `path` | 无 |

### 通信

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `queryContacts` | 按姓名查通讯录 | `name`, `limit` | `READ_CONTACTS` |
| `makePhoneCall` | 打电话（联系人名或号码） | `contactName` / `phoneNumber` | `READ_CONTACTS` |
| `sendSms` | 发短信 | `contactName` / `phoneNumber`, `message` | `READ_CONTACTS` |

### 日历

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `createCalendarEvent` | 创建日程 | `title`, `startTime`, `endTime`, `location`, `description`, `reminderMinutes` | `WRITE_CALENDAR`, `READ_CALENDAR` |
| `queryCalendarEvents` | 查日程 | `date`, `days` | `READ_CALENDAR` |

### 通知 & 自动化

| 函数 | 说明 | 参数 | 权限 |
|------|------|------|------|
| `send_notification` | 发送系统通知 | `title`, `content` | 无 |
| `create_automation` | 创建定时规则 | `name`, `cron`, `steps` | 无 |

---

## 二、MCP 远程工具（8 个，Server 端）

| 工具 | 说明 | 客户端路由 |
|------|------|-----------|
| `weather_current` | 实时天气 + 预报 | 关键词/LLM → McpClient → Server |
| `translate_text` | 文本翻译 | 关键词/LLM → McpClient → Server |
| `news_headlines` | 新闻头条 | LLM → McpClient → Server |
| `geo_location` | IP 地理位置 | LLM → McpClient → Server |
| `qrcode_generate` | 生成二维码（base64 PNG） | LLM → McpClient → Server |
| `web_search` | 互联网搜索 | LLM → McpClient → Server |
| `email_send` | 发送邮件（HTML 支持） | LLM → McpClient → Server |
| `document_summarize` | 文档摘要/润色 | LLM → McpClient → Server |

---

## 三、可用流程

### 关键词路径（无 LLM 时）

| 用户说 | 命中 | 类型 |
|--------|------|------|
| "这是什么手机" | getDeviceInfo | local |
| "我连的什么网" | getNetworkType | local |
| "屏幕分辨率多少" | getScreenInfo | local |
| "还剩多少空间" | getStorageInfo | local |
| "把 hello 复制到剪贴板" | setClipboard | local |
| "剪贴板有什么" | getClipboard | local |
| "写一个文件，内容是..." | writeFile | local |
| "打开这个文件" | readFile | local |
| "通知我5分钟后开会" | send_notification | local |
| "找一下张三的电话" | queryContacts | local |
| "给张三打电话" | makePhoneCall | local |
| "给李四发短信说我到了" | sendSms | local |
| "明天下午3点开会记进日历" | createCalendarEvent | local |
| "我今天有什么安排" | queryCalendarEvents | local |
| "把 hello 编码成 base64" | base64 | local |
| "今天早上8点提醒我打卡" | create_automation | local |
| "北京今天天气" | weather_current | mcp |
| "翻译 hello 成中文" | translate_text | mcp |

### LLM 路径（ServerLlmClient 可用时）

支持任意自然语言输入，LLM 自动拆解为多步调用链。典型场景：

| 场景 | LLM 生成流程 |
|------|-------------|
| "查北京天气并通知我" | `weather_current` → `send_notification` |
| "先查北京天气再翻译结果成英文" | `weather_current` → `translate_text` |
| "分析数据：1月100万，2月120万...发微信" | LLM 分析 → `writeFile` → `shareFile` |
| "每天早上8点发北京天气" | `create_automation` (含 `weather_current` + `send_notification`) |
| "搜索今天的科技新闻翻译成中文" | `web_search` → `translate_text` |

---

## 四、办公场景分类

| 场景 | 用户说 | 函数 | 状态 |
|------|--------|------|------|
| **创建日程** | "明天下午3点开会" | createCalendarEvent | ✅ |
| **查日程** | "我今天有什么安排" | queryCalendarEvents | ✅ |
| **查号码** | "找一下张三的电话" | queryContacts | ✅ |
| **打电话** | "给张三打电话" | makePhoneCall | ✅ |
| **发短信** | "给李四发短信说我到了" | sendSms | ✅ |
| **天气** | "北京今天天气" | weather_current (MCP) | ✅ |
| **翻译** | "翻译 hello 成中文" | translate_text (MCP) | ✅ |
| **搜索** | "搜索今天的科技新闻" | web_search (MCP) | ✅ LLM 路径 |
| **定时规则** | "每天早上8点提醒我打卡" | create_automation | ✅ |
| **记笔记** | "记一下今天完成了会议纪要" | writeFile | ✅ |
| **分享文件** | "把这个文件发到微信" | shareFile | ✅ |
| **发邮件** | "发邮件给a@b.com说..." | email_send (MCP) | ✅ LLM 路径 |
| **文档摘要** | "帮我总结这段文字" | document_summarize (MCP) | ✅ LLM 路径 |

---

## 五、执行路径总览

| 路径 | 触发条件 | 能力范围 |
|------|---------|---------|
| **关键词** | LLM 不可用或解析失败 | 17 个本地函数 + 天气/翻译 MCP |
| **LLM** | ServerLlmClient 可用 | 全部函数 + MCP + 多步推理 |
| **自动化** | WorkManager 定时触发 | 任意函数组合（mcp+local） |
