# 第三方组件说明

本仓库的源码不包含任何第三方 Java 依赖（只用到 Android SDK 与 JDK 标准库），代码以 [MIT](LICENSE) 授权。

下面这些**不随仓库分发**，需要使用者自行获取，因此不构成本仓库的分发内容：

## FFmpeg

自动转码功能会在设备上调用 `/data/local/tmp/ffmpeg`。常见的静态构建（尤其是带 `--enable-gpl --enable-libx264` 的）以 **GPLv3** 授权，分发时必须同时提供对应源码与许可证文本。

- 本仓库不提供 ffmpeg 二进制，也不在 Release 附件中提供
- 你自己编译或下载的构建，其许可证义务由你承担
- 用 `ffmpeg -version` 可以查看构建参数与许可证

## Android SDK Platform-Tools (adb)

`scripts/install.sh`、`scripts/push-ffmpeg.sh` 会优先使用 `tools/platform-tools/adb`，找不到就回落到 `PATH` 里的 `adb`。Platform-Tools 受 Android SDK 条款约束，本仓库不收录，请从官方渠道获取。

## Android 系统与厂商组件

项目会调用系统自带的 `su`、`am`、`pm`、`input` 等命令，以及在部分机型上改写厂商预装守护进程的配置。这些组件归属各自的厂商与 Android 开源项目，本项目只调用、不包含。

