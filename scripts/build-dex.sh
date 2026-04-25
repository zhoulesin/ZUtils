#!/bin/bash
set -e

# ============================================================
# ZUtils DEX 构建脚本
# 用法: ./scripts/build-dex.sh
#
# 从 TestFun 模块构建 DEX 并自动输出到 plugin-manager 的 assets
# ============================================================

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

# 版本号（与 TestFun/QRCodeFunction.kt 中的 VERSION 保持一致）
PLUGIN_VERSION="1.0.0"
ZXING_VERSION="3.5.3"

echo "🔧 构建 TestFun 模块..."
./gradlew :TestFun:assembleDebug

echo "📦 提取 classes.jar..."
mkdir -p build/dex-workdir/plugin
unzip -qo TestFun/build/outputs/aar/TestFun-debug.aar classes.jar -d build/dex-workdir/plugin

# 读取 SDK 路径
SDK_DIR=$(grep "^sdk\.dir" local.properties | cut -d= -f2)
BT_DIR="$SDK_DIR/build-tools"
BT_VER=$(ls "$BT_DIR" | sort -V | tail -1)

echo "🔧 SDK: $SDK_DIR"
echo "🔧 Build Tools: $BT_VER"

# 生成插件 DEX
mkdir -p build/dex-workdir/out
"$BT_DIR/$BT_VER/d8" --release --output build/dex-workdir/out \
  --lib "$SDK_DIR/platforms/android-36/android.jar" \
  build/dex-workdir/plugin/classes.jar

PLUGIN_DEX="plugin_qrcode_v${PLUGIN_VERSION}.dex"
cp build/dex-workdir/out/classes.dex "plugin-manager/src/main/assets/dex/$PLUGIN_DEX"
echo "✅ Plugin DEX 已生成: plugin-manager/src/main/assets/dex/$PLUGIN_DEX"

# 生成 zxing DEX（仅首次需要）
if [ ! -f "plugin-manager/src/main/assets/dex/lib_zxing_v${ZXING_VERSION}.dex" ]; then
  echo "📦 首次生成 zxing DEX..."
  ZXING_JAR=$(find ~/.gradle/caches -name "core-${ZXING_VERSION}.jar" -not -name "*sources*" -not -name "*javadoc*" 2>/dev/null | head -1)
  if [ -n "$ZXING_JAR" ]; then
    mkdir -p build/dex-workdir/zxing
    rm -rf build/dex-workdir/out/*
    "$BT_DIR/$BT_VER/d8" --release --output build/dex-workdir/out \
      --lib "$SDK_DIR/platforms/android-36/android.jar" \
      "$ZXING_JAR"
    cp build/dex-workdir/out/classes.dex "plugin-manager/src/main/assets/dex/lib_zxing_v${ZXING_VERSION}.dex"
    echo "✅ zxing DEX 已生成: plugin-manager/src/main/assets/dex/lib_zxing_v${ZXING_VERSION}.dex"
  else
    echo "⚠️ 未找到 zxing core-${ZXING_VERSION}.jar，请先 ./gradlew :TestFun:assembleDebug 以下载依赖"
  fi
else
  echo "⏭️  zxing DEX 已存在，跳过"
fi

# 清理
rm -rf build/dex-workdir
echo "🎉 完成"
ls -lh plugin-manager/src/main/assets/dex/
