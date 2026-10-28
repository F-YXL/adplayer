# 贡献指南

欢迎 issue 和 PR。项目很小，规则也简单。

## 提 Issue

- 用自带模板（Bug / 功能建议）
- 写清设备型号、Android 版本、adplayer 版本、复现步骤、期望结果、实际结果
- 日志：`adb logcat -s AdPlayer:*`
- **不要**贴真实 IP、MAC、序列号、素材内容或任何个人信息；需要时用 `192.0.2.10` 这类保留地址代替

## 提 PR

1. 从 `main` 切分支：`feat/xxx`、`fix/xxx`、`docs/xxx`
2. 改完先本地构建通过：`ANDROID_TOOLCHAIN=... KEYSTORE=... ./scripts/build.sh`
3. 提交信息格式：`类型: 一句话说明`（feat / fix / docs / chore / refactor）
4. PR 描述里写清楚改了什么、怎么验证的；涉及界面或遥控器的改动请附截图（截图先脱敏）
5. 一个 PR 只做一件事

## 代码约定

- 目标 Android 4.4.4（API 19），**不能**用 AndroidX、Lambda、try-with-resources 等 Java 8 语法
- 控件必须能用方向键选中、OK 触发（遥控器优先）
- 不引入第三方二进制：APK、图片素材、ffmpeg、platform-tools 一律不入库

## 提交前自查

- [ ] `git diff` 里没有 IP / MAC / 真实路径 / 口令
- [ ] 没有新增二进制文件
- [ ] 构建通过，APK 体积没有明显膨胀

