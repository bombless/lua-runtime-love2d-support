# Phase B2 — restricted pairing entry and Runtime consent

Status: host-side implementation and regression verification completed; partial cross-process target-device acceptance passed; Runtime consent approval and remaining lifecycle acceptance are still pending.

## Wire surface

B2 adds the separately versioned `com.bombless.luaandroid.ipc.v2.pairing` AIDL package to both projects. `PairingRequest` schema v1 carries only the exact requested Scope IDs. `PairingRequestResult` carries an opaque request handle, lifecycle status, and monotonic expiry. The service exposes only:

- `submitPairingRequest(PairingRequest)`
- `getPairingRequestStatus(requestId)`
- `cancelPairingRequest(requestId)`

There is deliberately no `getProtocolInfo`, `openSession`, Runtime metadata, or business method on this interface. The v2.0 `ClientHello`, bootstrap AIDL, and existing Parcelable field order are unchanged.

## Runtime enforcement

- `RestrictedPairingService` is exported without the Runtime signature permission solely to allow separately signed apps to request pairing.
- Every Binder method resolves identity from `Binder.getCallingUid()`, the complete PackageManager UID package set, package ownership, and current signer SHA-256 set. Ambiguous/shared UIDs and changed identities fail closed. No client-supplied package name is accepted.
- Requests accept only schema v1 and the seven known Scope IDs, reject duplicates/empty sets, are bounded to 32 pending challenges, expire after two minutes using elapsed realtime, and are rate-limited to five submissions per UID per ten minutes. Retained request/rate-limit bookkeeping is bounded.
- The Runtime consent screen obtains pending data only from Runtime-held challenge state. It displays the PackageManager-derived app label/package, current signer fingerprint, and exact requested Scopes. All Scope checkboxes start unchecked; the user must explicitly select at least one.
- Approval re-verifies the caller identity, consumes the one-time challenge, and writes only the selected Scopes using the B1 `AtomicPairingStore`. Denial, cancellation, expiry, identity change, unreadable persistence, and write failure grant nothing.
- The consent Activity is non-exported. The service first attempts to present it directly, then falls back to a notification. On Android 13+, a missing `POST_NOTIFICATIONS` grant now makes that fallback unavailable instead of silently leaving an unseen pending request. Notification PendingIntents include a unique challenge data URI so extras from hash-colliding request IDs cannot replace each other.
- `RuntimeV2Service` remains exported behind `com.bombless.luaandroid.permission.USE_LUA_RUNTIME` with `protectionLevel="signature"`; its AIDL and bootstrap/session behavior were not changed by B2.

## Verification performed

- Runtime final forced run: `testDebugUnitTest compileDebugKotlin assembleDebug --rerun-tasks --no-daemon` — BUILD SUCCESSFUL; 41 Gradle tasks executed.
- Runtime unit tests — 40 tests, 0 failures, 0 errors, 0 skipped. The final XML reports include 8 new `RestrictedPairingServiceBinderTest` cases.
- B2 negative tests invoke the generated `IRestrictedPairingService.Stub` locally under Robolectric with an injected `PackageIdentitySource`. They cover missing/invalid caller identity, UID/package mismatch, shared UID, absence of a client-supplied package identity, malformed/unknown/duplicate Scope requests, signer change at the consent boundary, challenge expiry and one-time replay, status/cancel lifecycle, denial, and persistence-write failure. These tests exercise the service Stub boundary but are **not** cross-process Binder/device tests.
- Companion `:love:compileNormalDebugJavaWithJavac --rerun-tasks --no-daemon` — BUILD SUCCESSFUL; AIDL and Java compilation executed.
- Existing v2.0 cross-project checker — passed (7 AIDL files, 5 Parcelable serialization-order pairs, signature permission declarations).
- B2 cross-project checker — passed (3 pairing AIDL files, 2 Parcelable serialization-order pairs, restricted interface method allow-list, Runtime manifest boundaries).
- `git diff --check` — passed in both repositories.
- Runtime debug APK was built at `app/build/outputs/apk/debug/app-debug.apk`; SHA-256: `24D28AF916D946DD9448D69A87EAB22C7909ADF2C26556D4C18D904F0AD51DDD`.
- The separately signed device-probe APK was built outside both repositories with a distinct SHA-256 signing certificate (`965e5e7daf48ae30abc1957daf95ad00c32f3ca8c511c6a00151bf88c948ece6`); it was installed and exercised on the RMX3800, then uninstalled after the partial acceptance run.

## Consent presentation investigation and corrective change (2026-10-31)

The previous service attempted `startActivity()` from a Binder-triggered Service and treated a non-throwing return as proof that the consent Activity was visible. That assumption is invalid on modern Android: background Activity launch policy can leave the Activity task behind the foreground app without throwing. On the RMX3800, inspection showed the consent Activity task was not foreground/visible and the `pairing-consent` notification channel had not been created, while Runtime's `POST_NOTIFICATIONS` runtime permission was granted.

Corrective change in `ipc-android/app/src/main/java/com/bombless/luaandroid/ipc/v2/pairing/RestrictedPairingService.kt`:
- Removed the direct background Activity launch.
- Always publish the Runtime-owned consent notification; its immutable PendingIntent is the explicit user-gesture route into the non-exported consent Activity.
- Retained the notification permission/channel checks, unique per-request data URI, and fail-closed behavior when no notification route is available.

Verification after the change:
- Runtime `testDebugUnitTest compileDebugKotlin assembleDebug --rerun-tasks --no-daemon`: BUILD SUCCESSFUL; 41 tasks executed.
- Unit tests: 40 passed, 0 failures, 0 errors, 0 skipped.
- Runtime APK SHA-256: `5A808E10B59B63FD1034B062C730AD30AAE7AC47E8E002F43FAF90B8F92909CB`.
- Runtime APK update installed successfully on RMX3800, confirming update-signature compatibility.
- Both v2.0 and B2 cross-project contract checkers passed; `git diff --check` passed in both repositories.

The separately signed probe APK was subsequently validated and successfully installed/launched on RMX3800 via `adb install -r` (`Success`). The APK ZIP is intact, manifest reports minSdk 26 / targetSdk 35 / compileSdk 36, and APK Signature Scheme v2 verification passes (signer SHA-256 `965e5e7daf48ae30abc1957daf95ad00c32f3ca8c511c6a00151bf88c948ece6`). Thus the user's package-parse UI error is not reproducible with the preserved APK through ADB; the earlier Oplus install intercept is not the current blocker.

On fresh probe launch, the UI reports `PAIRING_BIND_REQUESTED=false`. Device log explicitly reports Oplus `OplusAppStartupManager` preventing startup of `com.bombless.luaandroid/...RestrictedPairingService` when requested by foreground `com.bombless.b2probe`. Current blocker is therefore OEM background-service/autostart policy on Runtime, not APK parsing. Do not bypass device security settings. Ask the user to allow Runtime (`com.bombless.luaandroid`) auto-launch/background startup through the phone's app-launch/battery settings, then retry the bind and verify the consent notification, explicit selected-Scope approval, persisted grant, denial, expiry, and re-application. B2 remains pending.

## Target-device acceptance: partial pass; consent remains blocked

Device: Realme RMX3800, Android 16 / API 36. The separately signed probe (`com.bombless.b2probe`, signing certificate SHA-256 `965e5e7daf48ae30abc1957daf95ad00c32f3ca8c511c6a00151bf88c948ece6`) was successfully installed and launched. The probe was built outside both repositories.

Observed on the physical device:

- [x] Restricted pairing service bind completed: `PAIRING_BIND=CONNECTED`.
- [x] `submitPairingRequest` returned `PENDING` and an opaque request ID.
- [x] Caller-bound status query returned `PENDING`.
- [x] The separately signed caller attempted the existing Runtime v2 protected path and received `DENIED_BY_SIGNATURE_PERMISSION`, confirming Android signature-permission isolation for this caller.
- [x] Probe cancellation returned true; a subsequent status refresh reported `STATUS=CANCELLED`.
- [x] Temporary probe package was uninstalled after the test; Companion was left installed and foreground.

Not verified on device:

- [ ] Runtime consent Activity/notification becoming visible and actionable. Inspection did not establish a successful user-approval surface; no approval was attempted or fabricated.
- [ ] Explicit approval of a selected Scope subset and verification of the persisted pairing/granted Scopes.
- [ ] Device denial, expiry, and re-application lifecycle.
- [ ] Broader cross-process hostile-client matrix; Robolectric local-Stub tests remain distinct from remote Binder tests.

B2 remains **PENDING** until the Runtime consent surface is made discoverable/actionable and approval plus remaining lifecycle tests pass. Do not start B3 or Phase C.