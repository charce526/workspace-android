# Code audit and regression checklist

Base: `4e5063422ca2421dc3f6be615eff67d639838283` (2026-09-22).
This is a source audit, not a penetration-test certification or a successful Android build.
The existing UI design and application ID are retained.

## Implemented fixes

- Restrict token injection to the configured HTTP(S) origin, including a second origin check inside the actual JavaScript. Invalid origins never compare equal.
- Distinguish unavailable network/server responses from explicit 401/403 authentication rejection. Do not automatically retry passwords on transient check failures.
- Pick up an automatically renewed token in the current WebView.
- Do not reuse a stored password after the server URL or account is edited. The user must enter a password for the new identity.
- Disable credential-bearing HTTP redirects, unrestricted file access, active mixed HTTP resources within HTTPS pages, and unsolicited popup creation.
- Route user-initiated new-window links using current settings, including same-origin new windows; restrict sign-in interception to the workspace origin.
- Dispose the main WebView, popup WebViews, JavaScript bridge and delayed handlers on exit. Defer popup destruction until after its callback returns and expire unused popup views.
- Retain URI grants from the system picker instead of copying large files on the UI thread. Preserve provider file names/MIME types; normalize comma-separated accept types; support media/file multiselect; complete callbacks once.
- Reject picker results using file:// or the application's own FileProvider. Camera URIs are created by the app separately.
- Sanitize download names and use app-specific Downloads on Android 8/9, avoiding an undeclared public-storage permission requirement.
- Make default/last-used workspace changes transactional. Add v1-to-v2 migration for the default flag; remove automatic destructive fallback. Because no v1 schema snapshot is in the repository, upgrade from an actual v1 installation remains a required device test.
- Exclude encrypted credentials and WebView data from backup/device transfer; serialize first-time Keystore key creation.
- Handle login/storage failures and DataStore I/O failures without unhandled coroutine crashes. Preserve cancellation in authentication paths.
- Fix floating-ball reset/resize updates, pixel-vs-dp touch threshold, and invalid clamp ranges in small windows.
- Move release signing secrets into untracked local configuration; remove account-bearing login logs and disable webpage console logging in non-debug builds.

## Release signing setup — required locally

1. Copy `keystore.properties.example` to `keystore.properties` in the repository root.
2. Fill the real values locally. Keep the existing `.jks` and alias so installed apps can upgrade. Do not generate a new signing identity merely for this patch.
3. Without all four properties, debug builds still work; release output is unsigned and is not ready to distribute.
4. Previously committed passwords still exist in Git history. Removing them from this revision is not history cleanup. Consider changing the keystore/key passwords locally while retaining the same key. This patch does not rewrite history.

## Executed checks

- `node tools/audit-regression.mjs`: 17 checks passed, including execution of the actual token-injection JavaScript for five allowed/blocked-origin cases and 12 source guards.
- All 16 tracked Android XML files parsed successfully.
- `git diff --check` passed.
- Added `WebUrlPolicyTest` (four JUnit test methods); NOT executed here.

No Android SDK or Kotlin compiler is available in the audit runtime. APK compilation, Android lint, JUnit execution and emulator/device tests are NOT claimed as passed.

## Required local checks

Run with the repository's configured toolchain (the checked-in Gradle wrapper uses 9.6.0 and daemon JVM criteria use Java 25; the old README's generic JDK17/Gradle8 guidance is not authoritative):

```bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Test Android 8/9 and a recent Android device:

- Fresh install, existing v2 upgrade, real v1 upgrade without losing workspaces.
- Multiple servers and two accounts on the same server; rapid switching; expired token recovery; explicit wrong password; offline, timeout, 429, 500 and 503 responses.
- Initial login redirects, relogin, server subpaths, reverse proxies, nonstandard ports; malicious external `/signin` links do not trigger native relogin.
- Toggle external-browser behavior without refresh: same-origin and cross-origin ordinary links, `target=_blank`, `window.open`; cancel unsupported special links safely.
- Camera accept/cancel, gallery image/video, single/multiple files, PDF/XLS/DOC/unknown types, large files, cloud providers, picker cancel then retry.
- Rotate or background the app while the camera/picker is open. Activity recreation cannot preserve a Chromium upload callback; the user may need to select again. Full upload resumption across process death is not implemented.
- Download Chinese/path-like names on Android 8/9 and current Android; verify storage location and authorization requirements.
- Repeatedly open/exit workspaces, change theme, reset the floating ball and resize/split screen; monitor memory.

## Remaining scope / compatibility decisions

- WebView CookieManager/localStorage remain shared by origin. Full per-workspace browser-profile isolation and reliable same-origin multi-account isolation require a dedicated design and device tests; not claimed fixed.
- Blob downloads and token-only protected downloads remain unsupported by the current DownloadManager path. Do not add Authorization headers indiscriminately to external downloads.
- Cleartext HTTP is still permitted for existing intranet deployments; it cannot protect passwords/tokens in transit. Use HTTPS for untrusted networks.
- HTTPS pages that reference HTTP subresources must fix those resource URLs; mixed content is now blocked.
- Only the existing basic authenticator is implemented. SSO, MFA, captcha and other authentication plugins are not validated.
- Camera cache retention and upload continuation after process death still need product-level limits/handling.
- README licensing badge points to a LICENSE file absent from the repository. The audit does not choose or grant a license on the owner's behalf.
- No repository dependency versions were changed; their complete resolution/build compatibility requires the local build above.

Security reference: https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
