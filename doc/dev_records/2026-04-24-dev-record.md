# 开发记录 — 2026-04-24

## 项目概述

**ZUtils** — 一个 AI 驱动的 Android 动态功能引擎。
用户用自然语言描述需求，AI 拆解为工作链（Workflow），顺序调用 App 内置函数；
若函数缺失，通过动态加载 DEX 补全能力。

---

## 当前进度

### Phase 1: 核心架构完成（22 个引擎文件）

#### 接口层（core/）— 7 个文件

| 文件 | 说明 |
|------|------|
| `ZFunction.kt` | 所有函数的顶层接口：`val info + suspend fun execute(context, args)` |
| `FunctionInfo.kt` | 函数元数据（名称、描述、参数定义、所需权限、来源） |
| `FunctionRegistry.kt` | 注册中心接口：`register / get / getAllInfos / contains / size` |
| `FunctionSource.kt` | 来源枚举：`BUILT_IN、DEX、REMOTE、USER_DEFINED` |
| `Parameter.kt` | 参数定义 + JSON Schema 类型（STRING/NUMBER/BOOLEAN/INTEGER/ARRAY/OBJECT） |
| `ZResult.kt` | 执行结果：`Success(data: JsonElement) \| Error(message, code, recoverable)` |
| `ExecutionContext.kt` | 执行上下文（Android Context、协程 Scope、FunctionRegistry、运行时状态） |

#### 实现层（registry/ + workflow/）— 4 个文件

| 文件 | 说明 |
|------|------|
| `DefaultFunctionRegistry.kt` | LinkedHashMap 实现的注册中心 |
| `Workflow.kt` | 数据模型：`WorkflowStep(function, args, description)` + `Workflow(steps)` |
| `WorkflowResult.kt` | 执行结果：`StepResult` 集合 + 总耗时 + 成功/失败 |
| `DefaultWorkflowEngine.kt` | 顺序执行引擎：遍历步骤 → 查 Registry → 调用 → 失败即停止 |

#### 基础设施接口（dex/ + llm/）— 3 个文件

| 文件 | 说明 |
|------|------|
| `DexLoader.kt` | DEX 加载接口：`resolve / download / load / getCacheDir` |
| `DexSpec.kt` | DEX 元数据：函数名、下载 URL、类名、版本、校验和、所需权限 |
| `LlmClient.kt` | LLM 接口：`parseIntent(userInput → Workflow)` + `summarize(result → String)` |

#### 顶层入口

| 文件 | 说明 |
|------|------|
| `Engine.kt` | Facade，统一入口。持有 Registry + WorkflowEngine + DexLoader + LlmClient |

#### 内置函数（functions/）— 15 个文件

| 函数 | 类别 | 参数 | 说明 |
|------|------|------|------|
| `CalculateFunction` | 工具 | `expression` | 四则运算（词法分析 → 后缀表达式 → 求值） |
| `GetCurrentTimeFunction` | 系统 | `format` | 当前时间，支持自定义格式 |
| `GetBatteryLevelFunction` | 系统 | — | 电池电量百分比 |
| `SetScreenBrightnessFunction` | 系统 | `level(0-100)` | 屏幕亮度 |
| `GetDeviceInfoFunction` | 系统 | `fields` | 设备品牌/型号/OS/SDK |
| `ToastFunction` | UI | `message` | Toast 消息提示（自动切主线程） |
| `UuidFunction` | 工具 | — | 生成随机 UUID v4 |
| `Base64Function` | 工具 | `action` + `text` | Base64 编码/解码 |
| `GetVolumeFunction` | 系统 | — | 当前媒体音量（current/max/percentage） |
| `SetVolumeFunction` | 系统 | `level(0-100)` | 设置媒体音量 |
| `SetClipboardFunction` | 工具 | `text` | 写入系统剪贴板 |
| `GetClipboardFunction` | 工具 | — | 读取系统剪贴板 |
| `GetScreenInfoFunction` | 系统 | — | 屏幕分辨率/密度/刷新率 |
| `GetStorageInfoFunction` | 系统 | — | 内置存储总量/已用/剩余 |
| `GetNetworkTypeFunction` | 系统 | — | 当前网络类型（wifi/mobile/none） |

---

### Phase 2: 交互 UI 完成

**MainActivity.kt** — 基于 Jetpack Compose + Material 3 的交互界面：

- 输入框 + 执行按钮
- 执行结果以卡片列表展示（成功绿色、失败红色）
- 异步执行（`CoroutineScope` + `launch`）
- `parseQuery()` 关键词匹配引擎（LLM Client 的 mock）：

| 关键词 | 命中函数 |
|--------|---------|
| 计算/calc | `calculate` |
| 时间/几点/time | `getCurrentTime` |
| 电量/电池/battery | `getBatteryLevel` |
| 亮度/brightness | `setScreenBrightness` |
| 设备/device/手机信息 | `getDeviceInfo` |
| toast/提示 | `toast` |
| uuid/随机码 | `uuid` |
| base64 | `base64` |
| 音量/volume | `getVolume` / `setVolume` |
| 剪贴板/clipboard | `getClipboard` / `setClipboard` |
| 屏幕/分辨率/screen | `getScreenInfo` |
| 存储/空间/storage | `getStorageInfo` |
| 网络/wifi/network/流量 | `getNetworkType` |
| 连续/然后 | 多步骤编排 |

---

### Phase 3: DEX 动态加载演示（TestFun 模块）

**TestFun/** — 独立 Android Library，用于演示 DEX 编译和动态加载。

| 文件 | 说明 |
|------|------|
| `QRCodeFunction.kt` | 基于 zxing 生成二维码，返回 `data:image/png;base64,...` |
| `engine/core/*.kt` | 7 个核心接口副本（与 App 一致，供独立编译） |
| `build.gradle.kts` | 独立构建配置 |
| `settings.gradle.kts` | 独立项目设置 |
| `README.md` | 完整 DEX 编译文档 |

**QRCodeFunction 参数**：`content`(文本)、`size`(像素)、`foreground`(前景色)、`background`(背景色)

**DEX 编译策略**：多 DEX classpath（方案 B）
- zxing-core 独立打为 `lib_zxing.dex`
- 插件代码独立打为 `plugin_qrcode.dex`
- 加载时用冒号拼合 `DexClassLoader("a.dex:b.dex", ...)`
- 后续其他插件可复用 `lib_zxing.dex`

---

## 依赖变更

**新增依赖**:
- `gradle/libs.versions.toml` — 新增 `kotlinx-serialization-json` 版本 + library 条目
- `app/build.gradle.kts` — 新增 `kotlinx.serialization` plugin + `kotlinx-serialization-json` library
- `build.gradle.kts` — 新增 `kotlin.serialization` plugin `apply false`

**TestFun 独立依赖**:
- `com.google.zxing:core:3.5.3`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0`

---

## 编译问题修复记录

| 问题 | 文件 | 修复 |
|------|------|------|
| `registerReceiver` 参数类型不匹配 | `GetBatteryLevelFunction.kt` | `Intent` → `IntentFilter` |
| `fail()` 缺少 `recoverable` 参数 | `ZResult.kt` | 给 companion `fail()` 加 `recoverable` 参数 |
| Toast 在后台线程崩溃 | `ToastFunction.kt` | 加 `withContext(Dispatchers.Main)` |
| 接口移动到 core 包 | `registry/FunctionRegistry.kt` | 搬到 `core/FunctionRegistry.kt` |
| Missing JsonElement import | `ExecutionContext.kt` | 加 `import kotlinx.serialization.json.JsonElement` |
| TestFun plugin 冲突 | TestFun 的 build 配置 | 独立 `settings.gradle.kts`，从 root 分离 |

---

## 项目文件结构总览（共 74 个文件）

```
ZUtils/
├── PRD.md                                          # 产品需求文档
├── build.gradle.kts                                # 根构建配置
├── settings.gradle.kts                             # 项目设置
├── gradle.properties
├── local.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml                          # 版本目录
│   ├── gradle-daemon-jvm.properties
│   └── wrapper/
├── app/                                            # 主 App 模块
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                                    # 资源文件
│       └── java/com/zhoulesin/zutils/
│           ├── MainActivity.kt                     # 交互界面
│           ├── ui/theme/                           # 主题定义
│           └── engine/                             # 引擎（22 个文件）
│               ├── Engine.kt                       # Facade
│               ├── core/                           # 核心接口（7）
│               ├── registry/                       # 注册中心实现（1）
│               ├── workflow/                       # 工作流引擎（4）
│               ├── dex/                            # DEX 加载接口（2）
│               ├── llm/                            # LLM 接口（1）
│               └── functions/                      # 内置函数（15）
└── TestFun/                                        # DEX 演示模块
    ├── README.md                                   # DEX 编译文档
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── src/main/kotlin/com/zhoulesin/zutils/
        ├── engine/core/                            # 核心接口副本（7）
        └── testfun/QRCodeFunction.kt               # 二维码函数
```

---

## 待办事项

1. **LLM Client 实现** — 对接 OpenAI/Claude Function Calling API，替代 `parseQuery`
2. **DexLoader 实现** — 完成 `DefaultDexLoader`，支持 assets/网络 DEX 加载
3. **权限管理** — 敏感函数执行前动态申请 Android 权限
4. **语音输入** — 集成语音识别
5. **工作链可视化** — 显示 LLM 拆解出的步骤列表和执行状态
6. **DEX 签名校验** — 安全沙箱
