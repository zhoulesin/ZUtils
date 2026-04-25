# plugin-manager — 插件管理器

负责 DEX 文件加载、版本管理和依赖解析。

## 职责

- `dex_manifest.json` 解析和版本映射
- DEX 文件缓存和加载（InMemoryDexClassLoader / DexClassLoader）
- 多 DEX classpath 管理（主 DEX + 依赖 DEX）
- 插件版本校验（`requiredDependencies` vs manifest）
- 异常处理和友好错误反馈

## 模块依赖

```kotlin
dependencies {
    implementation(project(":engine-core"))
}
```

## 包结构

```
com.zhoulesin.zutils.plugin/
└── DefaultDexLoader.kt        # DEX 加载实现 + manifest 模型
```

assets 目录包含 DEX 文件和版本清单：

```
src/main/assets/dex/
├── dex_manifest.json           # 版本映射表
├── plugin_qrcode_v1.0.0.dex   # 插件 DEX（文件名自带版本号）
└── lib_zxing_v3.5.3.dex       # 依赖 DEX
```

## dex_manifest.json 格式

```json
{
  "plugins": [
    {
      "functionName": "generateQRCode",
      "version": "1.0.0",
      "dexUrl": "dex/plugin_qrcode_v1.0.0.dex",
      "className": "com.zhoulesin.zutils.testfun.QRCodeFunction",
      "dependencies": [
        { "name": "zxing-core", "version": "3.5.3", "dexUrl": "dex/lib_zxing_v3.5.3.dex" }
      ]
    }
  ]
}
```

## 插件升级流程

1. 修改 `TestFun/QRCodeFunction.kt` 中的 `VERSION` 和 `requiredDependencies`
2. 运行 `./scripts/build-dex.sh` 自动编译并更新 DEX 文件
3. 手动更新 `dex_manifest.json` 中的版本号和 dexUrl（如有变更）
