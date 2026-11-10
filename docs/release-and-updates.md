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

## Release 体积

- Release 启用 R8 和资源裁剪，沿用 SQLCipher JNI 与 Room 的依赖保留规则；生成四种 ABI 的独立完整 APK，并保留 universal APK 供旧客户端和未知架构回退。
- 本地脚本与 CI 按明确文件名收集五个 APK，禁止任取第一个 APK 当作通用包。通用包沿用 `xome-<版本>-release.apk`；架构包命名为 `xome-<版本>-<ABI>-release.apk`，可单独安装，无需配套其他 APK。
- 新客户端按 `Build.SUPPORTED_ABIS` 的优先顺序选择对应架构包；未发布匹配包时使用通用包。仓库地址、摘要、版本和签名检查仍生效，缓存保留选中的资产名。
- 精确排除未使用的 Bouncy Castle Picnic `*.bin.properties` 数据表，不排除 Argon2id、Blake2b 或 AES 代码。
- 另排除不可达的 PKIXCertPathReviewer 对应提示文本，开启 release PNG crunch。内置 JPEG 已按展示用途缩放、以质量 82 重新编码；可用 Windows 脚本 `scripts/optimize-images.ps1` 生成预览，检查后加 `-Apply` 写入。脚本首次执行会将输入图备份到 `artifacts/image-originals/`，重复优化从备份生成，避免反复有损压缩。`artifacts/` 中的备份及预览目录不参与打包。
- Release APK 同时压缩 DEX，进一步减少 GitHub 分发文件大小；安装时由系统处理代码解压与优化，因此下载体积不等于安装占用。此选项不改变从 AAB 生成 APK 的 DEX 打包方式。
- APK 与 AAB 必须分别调用 Gradle：APK 使用 `assembleRelease -PsplitApks=true`，AAB 使用 `bundleRelease -PsplitApks=false`。本地 `all` 和 CI 已按此顺序执行，避免 AGP 资源裁剪器不支持多 APK 输出同时构建 AAB 的限制。单独调用 `bundleRelease` 时也默认关闭 ABI 拆包。
- Release 原生库使用 `useLegacyPackaging=true` 压缩，安装器按设备 ABI 解压到原生库目录供 `System.loadLibrary` 使用。代价是安装时解压、保留提取后的库文件；不能把 APK 下载大小直接视为安装占用。Debug 打包方式不变。
- 构建后核验 APK 中 `.so` 已压缩、合并清单 `extractNativeLibs=true`、四种 ABI 完整、解压后的库哈希与旧包一致，以及 release 签名有效；保存对应的 R8 `mapping.txt`。静态核验不能替代隔离设备上的加密数据库和 Argon2/AES 运行测试。
- 配置依据：[AGP 原生库打包 API](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/JniLibsPackaging) 与 [Android 安装器提取说明](https://developer.android.com/guide/topics/manifest/application-element#extractNativeLibs)。

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

- 应用启动及回到前台时自动检查，前台每 15 分钟唤起检查逻辑；检查成功后 6 小时内复用结果，失败后最早 15 分钟重试。进入后台后取消该循环，返回前台重新开始。无需解锁私密空间，也不新增常驻服务或调度依赖。
- 计算器工具栏提供应用更新入口；发现新版本时显示通用更新提示，每个版本可选择“稍后”，稍后仍保留更新徽标。私密空间设置中的更新入口继续保留。
- 用户可手动选择正式版或 Beta，手动检查可绕过检查间隔。Release 元数据经 HTTPS 获取，只接收目标仓库按版本命名的正式签名 APK；请求不包含保险库密码、文件或用户数据。
- 用户明确点击下载后交由 Android DownloadManager；下载可由系统续传，返回设置页时读取状态。
- APK 经过下载大小、可用 SHA-256、包名、versionCode 递增及签名身份核验后，**用户再次点击安装**才启动系统安装器。不会自动安装。
- 打开安装器或未知来源权限设置会触发 Xome 的正常自动锁定；返回后无需解锁即可继续操作更新，重新访问私密内容仍需解锁。
- 自动检查失败不打断计算器使用，错误信息可在更新页查看；下载失败可取消/重试。没有公开 Release 时显示无可安装版本，而不是伪报最新。下载和安装仍须用户分别确认。

## 验收建议

- 前台周期更新改动已通过 66 项单元/Compose 测试、签名 APK 构建和完整 release Lint（0 错误，100 条既有警告）。本次未改版本号、未发布 GitHub；ARM64 本地验证包为 4,123,382 字节，位于 `artifacts/android/foreground-updates/`。
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。
- 用隔离的测试包或干净模拟器验证：普通算式和无效序列按 `=` 无校验提示、不锁住按键；合法序列可进入；系统切后台仍自动锁定。
- 验证设置页 Stable/Beta 切换、检查更新成功/失败、无 Release、下载暂停/恢复/取消、校验失败、签名不匹配、版本不可覆盖、权限引导、安装器恢复及重进应用。
- 无需解锁，验证计算器工具栏更新入口和新版本提示；选择稍后仍显示徽标。真机检查启动/前台恢复、停留前台的周期检查、后台取消，以及权限设置返回后的安装流程。周期逻辑已有虚拟时间测试，真机长时间运行仍需验收。
- 验证正式 Release 的 APK 签名、包名、版本号以及下载资产文件名和地址。
