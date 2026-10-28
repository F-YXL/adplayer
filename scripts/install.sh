#!/usr/bin/env bash
# 安装到设备
#
# 需要环境变量：DEVICE_IP（设备的局域网 IP）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${DEVICE_IP:?请设置 DEVICE_IP（设备局域网 IP）}"
IP="$DEVICE_IP"
ADB="$ROOT/tools/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb)" || { echo "找不到 adb，请把 platform-tools 放到 tools/ 或装进 PATH"; exit 1; }
VERSION="$(grep -o 'android:versionName="[^"]*"' "$ROOT/src/AndroidManifest.xml" | head -1 | cut -d'"' -f2)"
APK="${APK:-$ROOT/build/adplayer-$VERSION.apk}"
[ -f "$APK" ] || { echo "找不到 $APK，先运行 scripts/build.sh"; exit 1; }
"$ADB" disconnect "$IP:5555" > /dev/null 2>&1 || true
"$ADB" connect "$IP:5555"
sleep 2
"$ADB" install -r "$APK"
echo "==> 安装完成（部分机型会自动重启，等 1-2 分钟）"

