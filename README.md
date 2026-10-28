# 广告机播放器 · adplayer

把一台 RK3128 平台的老式 Android 广告机（电视盒子）改造成「视频 + 图片循环播放器」：
全部操作都能用红外遥控器完成，素材用手机浏览器上传，不需要电脑。

面向 Android 4.4.4（API 19）这类老设备：不依赖 Gradle / AndroidX，
用 `aapt + javac + d8` 直接打包，APK 不到 100 KB。

## 特性

| 模块 | 说明 |
|---|---|
| 播放 | 单屏 / 左右分屏 / 上下分屏 / 四宫格；视频 + 图片混播；列表循环 / 单文件循环 |
| 遥控器 | ↑ 快捷抽屉，↓ 切换循环模式，←/→ 上一个/下一个，OK 暂停/继续（长按进设置） |
| 设置 | 全部设置项支持方向键 + OK + 数字键，不弹软键盘 |
| 定时 | 定时开机（RTC 唤醒）、定时关机、定时重启、软件熄屏/亮屏 |
| 守护 | 定时把播放器拉回前台，防止被系统杀掉 |
| 手机端 | 网页管理素材：上传（带进度）、缩略图、批量删除、自动分类、剩余空间 |
| 转码 | 调用设备上的 ffmpeg，非 H.264 视频自动转码，解决「MP4 也播不了」 |

## 设备与系统要求

- Android 4.4.4（API 19）及以上的老机型，需要可用的 root（`su`）
- 建议 1080p、1 GB RAM 起步；硬件解码只稳 1 路视频，分屏时建议只有第 1 区放视频
- 只在 RK3128 广告机上做过完整验证，其他机型的 RTC、守护进程等厂商标识需要自行适配

## 快速开始

### 0. 直接下载（推荐）

到 [Releases](https://github.com/F-YXL/adplayer/releases) 下载 `adplayer-<版本>.apk`，拷到 U 盘安装，或 `adb install -r adplayer-<版本>.apk`。

> 1.2.0 起包名改为 `io.github.fyxl.adplayer`：从 1.1.0 及更早版本升级时系统会当成两个应用，**需要先卸载旧版**（设置项会丢，素材文件不受影响）。

### 1. 准备构建环境

需要 JDK 8 与 Android SDK（platforms/android-19 + build-tools 34.x），以及一个签名用的 keystore。
仓库不附带这两样东西，用环境变量指向它们：

```bash
export ANDROID_TOOLCHAIN=/path/to/android-toolchain   # 内含 jdk/ 与 sdk/
export KEYSTORE=/path/to/your.keystore
export KEYSTORE_ALIAS=your_alias
export KEYSTORE_PASS=your_password
export KEY_PASS=your_password
```

### 2. 构建

```bash
./scripts/build.sh            # 产物：build/adplayer-<版本>.apk
```

### 3. 安装到设备

```bash
export DEVICE_IP=192.0.2.10   # 换成你设备的局域网 IP
./scripts/install.sh
```

### 4. 手机管理素材

设备启动后，播放器会在局域网内开一个网页服务。手机连同一个 Wi-Fi，浏览器打开：

```
http://<设备IP>:8080
```

设置页顶部也会显示当前地址。

## 目录结构

```
.
├── README.md              本文件
├── CHANGELOG.md           版本历史
├── docs/
│   ├── 使用手册.md         给使用者看的完整说明
│   ├── 技术笔记.md         构建、设备、排错
│   └── 版本规范.md         版本号规则
├── src/                   Android 源码（AndroidManifest.xml + res + src）
├── scripts/
│   ├── build.sh           一键构建
│   ├── install.sh         安装到设备
│   └── push-ffmpeg.sh     推送 ffmpeg 到设备
└── build/                 构建产物（不入库）
```

## 配置（环境变量）

| 变量 | 用途 |
|---|---|
| `ANDROID_TOOLCHAIN` | 工具链根目录（含 `jdk/` 与 `sdk/`） |
| `KEYSTORE` | 签名 keystore 路径 |
| `KEYSTORE_ALIAS` | 签名别名 |
| `KEYSTORE_PASS` / `KEY_PASS` | 签名密码 |
| `DEVICE_IP` | 设备局域网 IP，安装 / 推文件时使用 |

## 已知限制

- 硬件解码只稳 1 路视频，分屏时只有区域 1 放视频，其余区域放图片
- 只支持 H.264 / AAC 的 MP4（H.265、10bit、4K 需转码，转码依赖设备上的 ffmpeg）
- 设备 Wi-Fi 不稳定时 adb 会周期性掉线，重连即可
- ffmpeg 放在设备 `/data/local/tmp/ffmpeg`，恢复出厂会丢失，用 `scripts/push-ffmpeg.sh` 重新推
- 仓库不含 ffmpeg 二进制，需要自行准备支持 libx264 的 armhf 静态构建（见 `docs/技术笔记.md`）

## 已知问题

- 页面上的「批量转码」按钮当前不可用：`/transcode_all` 会被 `/transcode` 的前缀匹配先命中（计划 1.2.2 修）
- 素材目录可以一路选到 `/mnt/sdcard` 根目录，此时网页端能看到设备上其它媒体文件，建议只选专用子目录

## 安全提示

本项目假定运行在**可信局域网**：

- 素材网页服务（8080）没有鉴权，同一网段内任何人都能上传 / 删除素材
- 应用需要 root，会在设备上执行 `su` 命令
- 不要把 8080 端口映射到公网

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请走 [SECURITY.md](SECURITY.md)，不要开公开 issue。

## 许可证

[MIT](LICENSE)

