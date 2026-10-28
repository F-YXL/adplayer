#!/usr/bin/env bash
# 把 ffmpeg 推到设备（恢复出厂或换机后执行）
#
# 需要环境变量：DEVICE_IP（设备的局域网 IP）
# 可选环境变量：FFMPEG_BIN（静态 ffmpeg 路径，默认 tools/ffmpeg-armhf）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${DEVICE_IP:?请设置 DEVICE_IP（设备局域网 IP）}"
IP="$DEVICE_IP"
ADB="$ROOT/tools/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb)" || { echo "找不到 adb，请把 platform-tools 放到 tools/ 或装进 PATH"; exit 1; }
FFMPEG_BIN="${FFMPEG_BIN:-$ROOT/tools/ffmpeg-armhf}"
[ -f "$FFMPEG_BIN" ] || { echo "找不到 $FFMPEG_BIN（仓库不附带 ffmpeg，见 docs/技术笔记.md）"; exit 1; }
"$ADB" connect "$IP:5555" > /dev/null 2>&1 || true
"$ADB" push "$FFMPEG_BIN" /data/local/tmp/ffmpeg
"$ADB" shell su -c "chmod 755 /data/local/tmp/ffmpeg"
"$ADB" shell su -c "/data/local/tmp/ffmpeg -version" | head -1
echo "==> ffmpeg 已就绪"

