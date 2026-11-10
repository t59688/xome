# Xome 的开发、签名、发布与应用内更新

该工作流根据相邻 PhonePulse 项目的 GitHub Releases / DownloadManager 方案移植，保持 Xome 的隐私约束和现有 Compose/Kotlin/Gradle 工程。

## 版本约定

- `VERSION` 是唯一版本源。支持 `X.Y.Z`（正式版）和 `X.Y.Z-beta.N`（预发布）。Git tag 为 `v` + `VERSION`。
- `versionCode = (major * 10000 + minor * 100 + patch) * 1000 + (beta ?: 999)`，同一版本号的 Beta 均小于正式版。
- 构建 APK 名称为 `xome-{VERSION}-release.apk`，AAB 同理。
- 新版本先修改 `VERSION` 并提交，再以同值创建 tag；Release 工作流会强制对齐 tag、VERSION、APK 元数据。

## Windows 开发与本地构建

- `dev.bat -List`：列出 ADB 设备及 AVD；`dev.bat -Serial emulator-5554` 安装调试包；也支持 `-Avd <名称>`。
- `build-release.bat apk` / `aab` / `all`：使用本机的独立 Xome release 签名，产物写入忽略目录 `artifacts/android/`。
- `scripts/capture_screenshots.py --serial emulator-5554 --name settings`：仅截取前台 Xome，不替用户输入解锁序列，遵守 Android FLAG_SECURE。
- 只有在本机离线环境缺失 Android Lint 依赖时，才使用 `build-release.bat apk -Offline -SkipLint` 来诊断签名与打包。该产物**不能代替**完成完整 Lint 的正式 CI Release 验收。

## 永久签名

1. 首次运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup-release-signing.ps1`。使用独立的 3072 位 RSA 签名密钥（非 PhonePulse 密钥）。
2. 它在本机生成 `.android-signing/xome-release.jks` 与 `.env.android.local`，并通过已登录的 `gh` 写入仓库 `t59688/xome` 的四个 Actions Secrets：`XOME_KEYSTORE_BASE64`、`XOME_STORE_PASSWORD`、`XOME_KEY_PASSWORD`、`XOME_KEY_ALIAS`。
3. **离线备份**密钥和密码文件（加密介质，两地保存）。首次发布后的 APK 必须继续使用相同签名。脚本检测到已有签名时会复用，不覆盖。
4. 上述敏感文件、产物目录和 `.env` 均在 `.gitignore` 内，不能放入公开仓库。

调试 APK 使用独立 debug 签名，不要试图以发布 APK 覆盖调试 APK。Android 会因为签名不同拒绝覆盖；不要为了测试而卸载现有私密数据。建议另用空白模拟器安装正式版。

## GitHub Actions

- `Android Release`：推送 `vX.Y.Z` 或 `vX.Y.Z-beta.N` tag 自动执行完整 Release 构建（APK+AAB）、版本元数据核验，并创建 GitHub Release；Beta tag 发布为 prerelease。**生产 CI 不跳过 Lint**。
- `Android Manual Build`：通过 GitHub Actions 手动选择 `apk/aab/all`，签名构建后上传短期 Actions Artifact，不创建 GitHub Release。
- 运行前要求仓库 Actions enabled、Secrets 正确且工作流文件已推送到目标分支。
- 本地 `gh` 检查命令：`gh secret list -R t59688/xome`、`gh workflow list -R t59688/xome`、`gh run list -R t59688/xome`。

## App 内更新与安全边界

- 更新入口仅显示在**解锁后的私密空间 → 设置 → 应用更新**。伪装计算器不自动检查更新、不弹窗、不展示徽章，也不在首次启动时请求 GitHub。
- 用户在设置页手动选择正式版或 Beta 并点击检查更新。Release 元数据经 HTTPS 获取，只接收目标仓库按版本命名的正式签名 APK。
- 用户明确点击下载后交由 Android DownloadManager；下载可由系统续传，返回设置页时读取状态。
- APK 经过下载大小、可用 SHA-256、包名、versionCode 递增及签名身份核验后，**用户再次点击安装**才启动系统安装器。不会自动安装。
- 打开安装器或未知来源权限设置会触发 Xome 的正常自动锁定；返回时需要重新解锁。
- GitHub 请求失败显示错误和重试，下载失败可取消/重试。没有公开 Release 时显示无可安装版本，而不是伪报最新。

## 验收建议

- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。
- 用隔离的测试包或干净模拟器验证：普通算式和无效序列按 `=` 无校验提示、不锁住按键；合法序列可进入；系统切后台仍自动锁定。
- 验证设置页 Stable/Beta 切换、检查更新成功/失败、无 Release、下载暂停/恢复/取消、校验失败、签名不匹配、版本不可覆盖、权限引导、安装器恢复及重进应用。
- 验证正式 Release 的 APK 签名、包名、版本号以及下载资产文件名和地址。
