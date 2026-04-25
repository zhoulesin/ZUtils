# ZUtils — AI 驱动的 Android 动态功能引擎

用户用自然语言描述需求，AI 拆解为工作链，顺序调用 App 函数。若函数缺失，动态加载 DEX 补全。

```
用户需求 → LLM 解析 → 工作链(Workflow) → 顺序调用函数 → 返回结果
                                          ↓ (函数缺失)
                                    动态加载 DEX → 继续执行
```

## 当前状态

已完成引擎框架 + 15 个内置函数 + 交互 UI + DEX 动态加载演示。LLM 对接待实现。

### 内置函数

| 关键词 | 函数 |
|--------|------|
| `计算 2+2*3` | 四则运算 |
| `时间` | 当前时间 |
| `电量` | 电池百分比 |
| `亮度 70` | 屏幕亮度 |
| `设备信息` | 设备参数 |
| `提示 你好` | Toast |
| `UUID` | 随机 UUID |
| `base64 编码 hello` | Base64 |
| `音量 50` / `音量` | 音量控制 |
| `复制 xxx 到剪贴板` / `剪贴板` | 剪贴板 |
| `屏幕信息` | 分辨率/密度 |
| `存储空间` | 存储用量 |
| `网络` | 网络类型 |
| `二维码 hello` | 二维码生成（DEX 动态加载演示） |
| `连续 计算 1+1 然后 时间` | 多步骤编排 |

### 模块架构

```
engine-core/     ← 接口+数据模型+引擎 (Engine, ZFunction, Workflow...)
  ↑
plugin-manager/  ← DEX 加载 + 版本管理 + manifest 解析
  ↑
app/             ← UI + 15 个内置函数
```

## 快速开始

```bash
# Android Studio 打开项目，Run 即可
```

## DEX 动态加载（关键演示）

输入 `二维码 hello`，引擎会：
1. 在 registry 中找不到 `generateQRCode`
2. 触发 `DefaultDexLoader` → 读取 `dex_manifest.json`
3. 使用 `InMemoryDexClassLoader` 从内存加载 `plugin_qrcode_v1.0.0.dex` + `lib_zxing_v3.5.3.dex`
4. 注册 `QRCodeFunction` → 执行 → 显示二维码图片

首次调用后日志显示版本校验信息。

## DEX 插件构建

```bash
./scripts/build-dex.sh
```

自动编译 `TestFun` → 生成 DEX → 输出到 `plugin-manager/src/main/assets/dex/`。

## 文档

- [PRD](PRD.md) — 产品需求文档
- [doc/dev_records/](doc/dev_records/) — 开发记录
- [engine-core/README.md](engine-core/README.md) — 引擎核心接口
- [plugin-manager/README.md](plugin-manager/README.md) — 插件管理器
- [TestFun/README.md](TestFun/README.md) — DEX 插件模块

## 项目工具链

本项目代码由 AI 编程助手 **OpenCode** (模型: deepseek-v4-flash) 辅助生成。
