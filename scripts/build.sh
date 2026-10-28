#!/usr/bin/env bash
# 一键构建 APK
#
# 需要环境变量：
#   ANDROID_TOOLCHAIN  工具链根目录，内含 jdk/ 与 sdk/
#   KEYSTORE           签名 keystore 路径
#   KEYSTORE_ALIAS     签名别名
#   KEYSTORE_PASS      keystore 密码
#   KEY_PASS           私钥密码（通常与 KEYSTORE_PASS 相同）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

: "${ANDROID_TOOLCHAIN:?请设置 ANDROID_TOOLCHAIN（含 jdk/ 与 sdk/ 的目录）}"
: "${KEYSTORE:?请设置 KEYSTORE（签名 keystore 路径）}"
: "${KEYSTORE_ALIAS:?请设置 KEYSTORE_ALIAS}"
: "${KEYSTORE_PASS:?请设置 KEYSTORE_PASS}"
: "${KEY_PASS:=$KEYSTORE_PASS}"

TC="$ANDROID_TOOLCHAIN"
JDK="$TC/jdk"
SDK="$TC/sdk"
PLATFORM="$SDK/platforms/android-19/android.jar"
BT="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)}"
BT="${BT%/}"
SRC="$ROOT/src"
OUT="$ROOT/build"

[ -f "$PLATFORM" ] || { echo "找不到 $PLATFORM"; exit 1; }
[ -x "$BT/aapt" ] || { echo "找不到 build-tools（$BT）"; exit 1; }
[ -f "$KEYSTORE" ] || { echo "找不到 keystore：$KEYSTORE"; exit 1; }

VERSION="$(grep -o 'android:versionName="[^"]*"' "$SRC/AndroidManifest.xml" | head -1 | cut -d'"' -f2)"
echo "==> 构建 adplayer $VERSION"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/apk"

export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_OPTS="-Xmx1024m"

"$BT/aapt" package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$PLATFORM" -F "$OUT/apk/app.unsigned.apk"

# 用相对路径编译，避免 sources.txt 里出现构建机的绝对路径
cd "$ROOT"
find src build/gen -name "*.java" | sed 's/.*/"&"/' > "$OUT/sources.txt"
javac -J-Xmx512m -source 1.7 -target 1.7 -encoding UTF-8 -bootclasspath "$PLATFORM" -d "$OUT/obj" @"$OUT/sources.txt"
find "$OUT/obj" -name "*.class" -print0 | xargs -0 "$BT/d8" --min-api 19 --lib "$PLATFORM" --output "$OUT/apk"

cd "$OUT/apk"
"$BT/aapt" add app.unsigned.apk classes.dex > /dev/null
"$BT/zipalign" -f 4 app.unsigned.apk app.aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEYSTORE_ALIAS" \
    --ks-pass env:KEYSTORE_PASS --key-pass env:KEY_PASS \
    --out "$OUT/adplayer-$VERSION.apk" app.aligned.apk
echo "==> 完成：$OUT/adplayer-$VERSION.apk"
ls -lh "$OUT/adplayer-$VERSION.apk"

