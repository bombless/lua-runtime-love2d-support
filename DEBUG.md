# B2 Device Pairing Debug Handoff

> 新上下文从这里接手。目标是定位 RMX3800 / Android 16 上独立签名 Probe 无法提交配对请求的原因。当前只做诊断与必要的最小修复；**不要批准任何 Scope**。

## 1. 工作区与项目

- 主项目：`D:\mcp-agent-workspace\love2d-runtime-bridge`
- Runtime：`D:\mcp-agent-workspace\ipc-android`
- 独立签名诊断 Probe：`D:\mcp-agent-workspace\.tools\b2-device-probe`
- 设备：RMX3800 / Android 16
- ADB serial：`adb-db6fad9-xYzn4j._adb-tls-connect._tcp`
- ADB：`D:\android-sdk\platform-tools\adb.exe`
- 当前文档：`docs\PHASE-B2-PAIRING-ENTRY.md`、`TODO.md`

## 2. 当前已知事实

### 已验证

- Runtime Phase B2 主机端实现已完成。
- Runtime 单元测试：40 tests，0 failures / errors / skipped。
- Runtime 构建成功：`testDebugUnitTest compileDebugKotlin assembleDebug --rerun-tasks --no-daemon`。
- 两仓库 `git diff --check` 通过。
- `python tools\verify-ipc-v2-contract.py` 和 `python tools\verify-ipc-v2-b2-contract.py` 通过。
- Runtime APK 已安装更新；SHA-256：
  `5A808E10B59B63FD1034B062C730AD30AAE7AC47E8E002F43FAF90B8F92909CB`
- Probe APK 已成功安装并启动（之前的一次 Oplus 安装拦截不是当前安装状态）。
- Probe manifest badging：package `com.bombless.b2probe`，minSdk 26，targetSdk 35，compileSdk 36。
- Probe APK 签名验证通过（v2）；signer SHA-256：
  `965e5e7daf48ae30abc1957daf95ad00c32f3ca8c511c6a00151bf88c948ece6`
- PackageManager 能将 `com.bombless.luaandroid.action.BIND_PAIRING_V1` 解析到 Runtime 的 `RestrictedPairingService`。
- Runtime 包安装状态正常（`stopped=false`），`RUN_ANY_IN_BACKGROUND=allow`。
- 历史设备记录 `docs/PHASE-B2-PAIRING-ENTRY.md` 记载：同一台 RMX3800 曾经 `PAIRING_BIND=CONNECTED`，`submitPairingRequest` 返回 `PENDING`，随后查询/取消返回 `CANCELLED`。因此此设备与配对协议此前确实成功过。
- 独立签名 Probe 调用 Runtime v2 被拒绝：`RUNTIME_V2=DENIED_BY_SIGNATURE_PERMISSION`，这是预期安全行为，不能“修复”或放宽。

### 当前故障现象

Probe 首页显示：
- `PACKAGE=com.bombless.b2probe`
- `ACTION=independent-signature pairing-only probe`
- `RUNTIME_V2=DENIED_BY_SIGNATURE_PERMISSION`（预期）
- `PAIRING_BIND_REQUESTED=false`
- `STATUS=NO_REQUEST`

Probe 按钮：`Request Pairing`、`Refresh Status`、`Cancel Request`、`Try Runtime v2 (must be denied)`。

Probe 的 pairing service 绑定在用户点击 Request Pairing 后才会走 `connectPairing()`，然后在 `onServiceConnected` 后提交请求。此前界面 `PAIRING_BIND_REQUESTED=false`，说明尚未成功走到绑定请求被确认的状态；但要检查代码如何更新这个字段，不能仅凭这个布尔值推断绑定失败阶段。

## 3. 重要日志边界

设备 logcat 曾写入以下分界线：
`10-31 14:50:12.599 I B2_DIAG_BOUNDARY: ========== B2_DIAGNOSTIC_BOUNDARY_20261031_01 : NEW LOGS AFTER THIS LINE ==========`

用户点击 Request Pairing 后，提取该 marker 之后的日志：
- 没有出现新的 `OplusAppStartupManager: prevent start ... RestrictedPairingService`
- 没有看到新的 ActivityManager / PackageManager 服务启动拒绝记录
- 因而**不能把当前故障断言为 Oplus 后台启动拦截或电池策略**。
- 早先确实出现过一次 Oplus 拦截日志，但它不在这次新分界线之后，不能作为当前点击失败的证据。

用户已明确表示：手机电池设置中 lua android 的后台行为完全允许；不要再让用户重复调电池/后台设置，也不要把它当作已确认根因。

## 4. 最优先调查方向

当前怀疑点是 Probe 的绑定调用方式与 Package Visibility / manifest 查询声明的组合：

- Probe 绑定使用 `setPackage("com.bombless.luaandroid")` + Action 的 Intent 解析方式，而不是显式 `ComponentName`。
- 优先只读检查 Probe：
  1. `app/src/main/AndroidManifest.xml` 的 `<queries>`、service/action 查询声明及权限声明。
  2. `MainActivity.java` 的 `connectPairing()`、`bindService()`、`onServiceConnected()`、`onServiceDisconnected()`、`ServiceConnection`、`PAIRING_BIND_REQUESTED` 更新逻辑。
  3. Probe AIDL/interface 定义及调用流程。
  4. 与历史成功 Probe 源码/构建产物/文档中绑定方式作对照（先找证据，不要猜）。
  5. Runtime manifest 中 pairing service 的 exported、permission、intent-filter、process 等属性；只读对照。
- 检查 bindService 返回值、异常、连接回调、Binder 类型转换、提交回调是否都有独立日志/状态。避免用一个布尔值代表多个阶段。
- 如果现有日志不足，可做最小诊断改动：对每个阶段使用不同日志标签/时间戳和返回值；不要改变 Runtime 安全边界。
- 若证据表明隐式 Intent/可见性导致问题，再考虑最小改为显式 `ComponentName("com.bombless.luaandroid", "com.bombless.luaandroid.ipc.v2.pairing.RestrictedPairingService")` 的诊断性尝试；先确认服务类名、导出配置、权限要求与调用者预期。不要未经检查就改。
- 每次设备测试前写入新的 logcat marker，随后只分析 marker 之后的日志，防止旧日志误导。

## 5. 建议先执行的只读检查

在 Windows PowerShell：

```powershell
$probe = ''D:\mcp-agent-workspace\.tools\b2-device-probe''
$runtime = ''D:\mcp-agent-workspace\ipc-android''

Get-Content "$probe\app\src\main\AndroidManifest.xml"
Get-ChildItem "$probe\app\src\main" -Recurse -File |
  Where-Object { $_.Extension -in ''.java'',''.kt'',''.aidl'' } |
  Select-Object -ExpandProperty FullName

Get-ChildItem "$runtime\app\src\main" -Recurse -File |
  Where-Object { $_.Name -match ''RestrictedPairingService|Consent'' } |
  Select-Object -ExpandProperty FullName

git -C $probe status --short
git -C $runtime status --short
```

然后逐个读取相关文件，不要一次输出整个仓库。特别检查 Probe 的 `MainActivity.java` 和 Runtime 的 `RestrictedPairingService.kt` / manifest。

设备诊断命令（每轮先写新 marker）：

```powershell
$adb = ''D:\android-sdk\platform-tools\adb.exe''
$serial = ''adb-db6fad9-xYzn4j._adb-tls-connect._tcp''
$marker = ''B2_DIAGNOSTIC_BOUNDARY_20261031_02''
& $adb -s $serial shell log -t B2_DIAG_BOUNDARY $marker
# 用户点击一次 Request Pairing 后：
& $adb -s $serial logcat -d -v threadtime -t 12000
```

从完整输出中定位 marker，再分析 marker 之后的日志。不要清空 logcat，除非有明确理由。

## 6. Runtime consent 通知入口改动背景

此前曾修改 Runtime 文件：
`app/src/main/java/com/bombless/luaandroid/ipc/v2/pairing/RestrictedPairingService.kt`

改动目的：不再从后台 Service 直接 `startActivity()`，而是发布 Runtime-owned consent notification；点击通知后通过显式 PendingIntent 打开非导出的 Consent Activity。保留通知 channel / 权限检查、每请求唯一 URI、fail-closed 行为及其余安全逻辑。

此改动已编译、单测通过并安装到设备。当前仍需验证审批入口实际可见性，但眼下更早的阻塞是 Probe 没有观察到正常 pairing bind/request 进展。先把绑定链路诊断清楚，不要把两个问题混在一起。

## 7. 安全与操作约束（必须遵守）

- 不批准任何 Scope，不点击或代替用户点击 consent 中的批准动作。
- 不启动 B3 / Phase C。
- 不改变 v2.0 ClientHello、RuntimeV2Service 或 Runtime v2 signature permission / 签名保护。
- 不放宽导出权限、signature permission、Binder 身份校验或 fail-closed 逻辑。
- 不 reset、清理、覆盖用户已有文件；修改前先检查 git diff/status。
- 不 commit，不 push。
- 优先只读诊断；若必须改动，保持最小范围并记录前后 diff、构建/测试结果。
- 不让用户重复更改电池优化/后台启动设置；目前没有新日志证据支持该方向。
- 不把历史 Oplus 拦截、历史安装失败或旧日志当作当前故障根因。
- 文档中若继续更新故障状态，明确写“根因未确认”，不要沿用“已确认 Oplus 后台拦截”的过时结论。

## 8. 本轮交接目标

先回答三个问题，再决定改动：
1. 点击 Request Pairing 后，Probe 是否真的调用了 `bindService()`？返回值是什么？是否抛异常？
2. 若 bind 返回 true，是否收到 `onServiceConnected()`？若没有，系统日志具体说明什么？
3. 若收到连接，是否成功取得 AIDL Binder 并提交请求？提交结果/异常是什么？

只有找到具体断点后，才做最小修复并重新构建、安装和验证。成功标准是：Probe 能提交 pairing request，Runtime 返回 PENDING，Runtime-owned consent 入口可见；后续 Scope 审批必须由用户本人明确操作。
