# TestFun — DEX 动态加载示例模块

包含 `QRCodeFunction`（二维码生成），编译为 DEX 后由主 App 动态加载。

## 模块结构

```
TestFun/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/zhoulesin/zutils/testfun/
        └── QRCodeFunction.kt
```

## 版本管理

版本号由插件源码维护：

```kotlin
class QRCodeFunction : ZFunction {
    companion object {
        const val VERSION = "1.0.0"
    }
    override val requiredDependencies: Map<String, String> get() = mapOf("zxing-core" to "3.5.3")
}
```

## 构建 DEX

方式一：一键脚本
```bash
./scripts/build-dex.sh
```

方式二：手动
```bash
./gradlew :TestFun:assembleDebug
unzip TestFun/build/outputs/aar/TestFun-debug.aar classes.jar -d /tmp/dp

~/Library/Android/sdk/build-tools/36.0.0/d8 --release --output /tmp/dx \
  --lib ~/Library/Android/sdk/platforms/android-36/android.jar \
  /tmp/dp/classes.jar

cp /tmp/dx/classes.dex plugin-manager/src/main/assets/dex/plugin_qrcode_v1.0.0.dex
```

构建产物自动输出到 `plugin-manager/src/main/assets/dex/`。

## 文件规范

DEX 文件名格式：`{name}_v{version}.dex`
- 插件：`plugin_qrcode_v1.0.0.dex`
- 依赖：`lib_zxing_v3.5.3.dex`
