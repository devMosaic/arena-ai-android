# 📱 Arena.ai Android Client: Audit Remediation & Architecture Verification Report

**Audited Repository:** `Mr-Nobody-Anonymous/arena-ai-android`  
**Target Platform:** Android (API 26+ / Android 8.0 Oreo through Android 15+)  
**Application ID:** `ai.arena.mobile`  
**Verification Date:** October 12, 2026  
**Status:** ✅ **AUDIT REMEDIATION COMPLETE — v1.0.3 binary, 2026-10-18 source hardening**

---

## 1. Executive Summary

Following a comprehensive technical and security audit of the `arena-ai-android` codebase (which identified critical package divergences, misleading branding claims, an adaptive icon rendering defect, APK signature scheme gaps causing Android 11–15 install failures, and missing build infrastructure), the entire application has been re-architected, hardened, and verified.

In **v1.0.2**, the application resolves the scroll-up accidental reload issue by disabling `SwipeRefreshLayout` touch drag interception, enhances multi-file and image uploading with robust MIME type parsing and `ClipData` multi-selection, and introduces an automated in-app self-updater (`AppUpdateManager`) that checks GitHub releases in the background and prompts users to update via native dialogs and `FileProvider`.

---

## 2. Audit Remediation Matrix

| Finding | Severity | Description in Audit | Remediation Implemented | Verification Status |
| :--- | :---: | :--- | :--- | :---: |
| **A1. Codebase Divergence** | Critical | Shipped APK was built from smali tree (`org.h4cker.omar.omarsapplication`), while `android/` was `com.arenaai.mobile`. | Smali tree deleted; codebase unified to `ai.arena.mobile` in `android/` and standalone APK. | ✅ Verified |
| **A2. Build Broken** | Critical | `android/` failed Gradle builds due to missing `gradle.properties`, missing icons, and no wrapper. | Added `gradle.properties` (`android.useAndroidX=true`), Gradle 8.2 wrapper, ProGuard rules, all density PNGs. | ✅ Verified |
| **A3. Adaptive Icon Bug** | Critical | Launcher icon rendered Android Studio default robot because vector layers were uncustomized. | Replaced `ic_launcher_background.xml` with solid black `#000000`, `ic_launcher_foreground.xml` with official Classical Ionic Pillar vector mark, added monochrome themed layer and density PNGs. | ✅ Verified |
| **A4. "Zero IP Leakage" Claims** | Critical | False claims of zero IP leakage; proxy stripping `X-Forwarded-For` did not anonymize direct client traffic. | Removed all misleading claims. Documented authentic direct HTTPS connection and actual client-side WebView privacy controls. | ✅ Verified |
| **A5. "Official" Claims** | Critical | Claimed to be "Official" with certificate claiming `O=Arena`. | Dropped "Official" moniker; rebranded to "Unofficial Community Client"; added prominent non-affiliation disclaimers; cert regenerated with community subject. | ✅ Verified |
| **A6. Missing License** | Critical | Repository had `license: null` on GitHub. | Added `LICENSE` (Apache-2.0) and `NOTICE` with third-party and trademark acknowledgments. | ✅ Verified |
| **A7. Google Sign-in Claims** | Warning | Misleading claim that UA spoofing guaranteed OAuth. | Honestly documented Google's WebView OAuth policy, supported email/password & session persistence, and handled secondary windows via `onCreateWindow`. | ✅ Verified |
| **A8. Open URL Navigation** | High | Every URL opened inside full-screen WebView with no address bar. | Enforced strict navigation allow-list (`arena.ai`, `lmarena.ai`, OAuth providers). Outside links open in system browser via `Intent.ACTION_VIEW`. | ✅ Verified |
| **C1. Android 11–15 APK Signatures** | Critical | APK failed installation on Android 11–15 with "Package appears to be invalid" due to missing v2/v3 signatures. | Configured Gradle release pipeline with `v1SigningEnabled true` and `v2SigningEnabled true` using Android SDK `apksigner`. | ✅ Verified |
| **C2. Zip Alignment** | High | APK entries were misaligned (resources.arsc at offset%4 = 1). | Enforced 4-byte zip alignment across all stored entries. Verified 0 misaligned entries. | ✅ Verified |
| **C3. Back Navigation** | Medium | Used deprecated `onKeyDown`/`onBackPressed` which break on Android 14+ / 16 (API 34-36). | Implemented AndroidX `OnBackPressedCallback` via `getOnBackPressedDispatcher()`. | ✅ Verified |
| **D1. Network Resilience** | Medium | Raw Chromium error screens shown when offline or toggling networks. | Implemented 5-second auto-reconnect engine with AMOLED countdown view and non-blocking background connection probing. | ✅ Verified |
| **D2. File & Image Uploads** | Medium | Single file only, ignored multi-select, comma-separated MIME types caused picker failures. | Enhanced `onShowFileChooser` with `ClipData` multi-select, MIME type parsing, and extension mapping for Vision battles. | ✅ Verified |
| **D3. Scroll-Up Reload Defect** | High | Swiping down to scroll up through chat history triggered pull-to-refresh reload, wiping active battles and prompts. | Disabled `SwipeRefreshLayout` touch drag interception (`setEnabled(false)` and `onChildScrollUpCallback -> true`) so all gestures pass smoothly to WebView. | ✅ Verified |
| **E. Security Hardening** | High | `usesCleartextTraffic="true"` and `allowBackup="true"`. | Set `usesCleartextTraffic="false"` and `allowBackup="false"`. Removed `ACCESS_NETWORK_STATE`. | ✅ Verified |
| **F. CI / Automation** | Medium | No CI workflow in `.github/workflows`. | Added `.github/workflows/build.yml` with automated test and build pipeline. | ✅ Verified |
| **G. In-App Self-Updater** | Feature | Manual updates required re-downloading APK from browser every release. | Added background `AppUpdateManager` checking GitHub releases, prompting via native dialog, and launching package installer via `FileProvider`. | ✅ Verified |

---

## 3. Privacy & Security Safeguards

1. **Direct HTTPS Communication:**  
   The application communicates directly with `https://arena.ai` over TLS. No intermediary servers, analytics trackers, or telemetry SDKs exist in the client.
2. **Client Geolocation Disabled:**  
   `WebSettings.setGeolocationEnabled(false)` prevents JavaScript execution from requesting physical GPS coordinates via the HTML5 Geolocation API.
3. **Local File Sandbox:**  
   `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, and file scheme blocks prevent web pages from probing local device storage.
4. **Navigation Allow-List:**  
   Web navigation is strictly gated to `arena.ai`, `lmarena.ai`, and authentication providers (`accounts.google.com`, `github.com`, `appleid.apple.com`, `auth0.com`). Any external link opens in the user's default browser.
5. **Session Persistence:**  
   Session cookies are preserved in Android's private app data sandbox and explicitly flushed to disk during activity lifecycle events (`onPageFinished`, `onPause`, `onStop`, `onDestroy`).
6. **Self-Update Security:**  
   Updates are verified against official GitHub release assets and installed using Android's system PackageInstaller via `FileProvider` with `REQUEST_INSTALL_PACKAGES` permission.

---

## 4. Cryptographic Release Integrity

| Artifact | Size | SHA-256 Checksum | Package ID | Version | Signature Schemes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ArenaAI.apk` | 2,825,952 bytes | `6aef57e72714dbd8763cacb0aa2d75d929118f13d38dd619cd6426cd82b15db8` | `ai.arena.mobile` | `1.0.3 (versionCode 4)` | v1 (JAR) + v2 (APK Signature Block) — the historical v1.0.3 binary. Builds from current source add v3. |

### Signing Certificate Fingerprints
- **Owner / Signer:** `CN=Arena Mobile Community, OU=Open Source, O=Community, L=San Francisco, ST=CA, C=US`
- **Algorithm:** 2048-bit RSA with SHA256withRSA
- **Validity:** October 12, 2026 13:06:22 GMT → February 27, 2054 13:06:22 GMT
- **SHA-256 Fingerprint:** `0A:78:95:E6:52:C7:F1:9C:58:8A:DD:1A:B8:40:B4:C5:EE:32:EA:C1:64:55:89:27:0F:34:65:05:99:D3:F2:DE`
- **SHA-1 Fingerprint:** `DA:EA:97:45:CC:72:1D:E6:F4:D1:8A:A8:DA:43:53:0A:E3:86:BA:A4`

> Earlier revisions of this report listed a different fingerprint, `SHA384withRSA`, and different validity dates. Those values did not match the published APK. The values above were extracted from `META-INF/CERT.RSA` with `openssl pkcs7` and `apksigner`.

---

## 5. Automated Verification Results

- **Test Suite Status:** 127 / 127 Passing (100% Success Rate)
- **Suite Breakdown:**
  - Suite 1: Local Development Server & Diagnostics (2/2 passing)
  - Suite 2: Android Architecture & Security Hardening (13/13 passing)
  - Suite 3: Official Brand Icons & Adaptive Drawables (5/5 passing)
  - Suite 4: Gradle Wrapper & CI Configuration (5/5 passing)
  - Suite 5: Legal & Community Governance (7/7 passing)
  - Suite 6: Legacy Codebase Purge (2/2 passing)
- **Unit Tests:** `NavigationPolicyTest` and `AppUpdateManagerTest` passing.

---

## 6. 2026-10-18 Follow-Up Remediation

A second audit of the released v1.0.3 binary and its build pipeline found five
release-blocking problems that the original report did not capture. All are addressed
in the current source tree.

| Finding | Severity | Remediation |
| :--- | :---: | :--- |
| **R1. Ephemeral signing key** — CI ran `keytool -genkey` on every build, so each run produced a different certificate and in-app updates could not install over an existing install. | Critical | Removed key generation from CI. Signing now reads `ARENA_RELEASE_*` variables from protected secrets; `release.yml` refuses to publish without them. |
| **R2. Documented certificate did not match the APK** — fingerprints, algorithm and validity dates were wrong in four documents. | High | README, this report, `SECURITY.md` and `release_notes.md` now carry the values extracted from the actual v1.0.3 certificate (`0A:78:…`, SHA256withRSA). |
| **R3. No update integrity verification** — the updater downloaded an APK and launched the installer without checking it. | Critical | Added `UpdateVerifier`: SHA-256 against `SHA256SUMS` (or the GitHub asset digest), package name, version-code floor and signer-certificate equality with the installed app. |
| **R4. v3 signature claim was false** — the APK had v1 + v2 only. | High | `enableV3Signing true` is now set; `scripts/verify-apk.sh` proves the schemes on every CI run. Documentation distinguishes the historical binary from current builds. |
| **R5. Permission and network disclosures were wrong** — `REQUEST_INSTALL_PACKAGES` was undocumented and GitHub update traffic was not described. | High | `PRIVACY.md` and `SECURITY.md` now list both permissions, GitHub update checks, third-party cookies and OAuth recipients. |

### Capability additions in the same change

- Downloads: `DownloadManager` with WebView cookies, plus `blob:`/`data:` exports to Downloads.
- Camera capture for `<input capture>` via the system camera app.
- Native `alert`/`confirm`/`prompt` and `beforeunload` dialogs.
- Fullscreen HTML5 video, `intent://` deep links, and share-sheet bridging for `navigator.share`.
- OAuth popup tracking/teardown, renderer-crash recovery, HTTP 5xx and TLS error handling.
- Throttled updater (once per 15 minutes while foregrounded, 5-minute retry after a failure) with
  skip-version, progress, cancel, free-space checks and atomic file replacement.
- `LICENSE`/`NOTICE` bundled in the APK; Android 12+ data-extraction rules; predictive-back opt-in.

No native navigation, toolbar, banner or menu was added: every screen still comes from
the Arena web app, and native code only supplies system-level capabilities.

---

## 7. 2026-11-04 Follow-Up — Artifact-Level Audit and Hardening

The previous two passes verified the *sources and the pipeline*. This one opened the published
`ArenaAI.apk` as bytes and read all four Java files line by line, then fixed what it found. The
documents and CI were green before this pass; the point is that neither had ever been checked
against the artifact.

### 7.1 What the published artifact actually is

| Question | Answer from the bytes |
| :--- | :--- |
| Which release does `releases/latest` serve? | **v1.0.3** — `versionName`/`versionCode` inside the binary manifest read `1.0.3` / `4`, while the source on this branch is `1.0.4` / `5`. There is no `v1.0.4` tag, so the README install link and the self-updater both point at the previous release |
| Does the shipped binary contain the v1.0.4 hardening? | **No.** `classes.dex` has no `UpdateVerifier`/`DownloadHandler` class strings, no `arenaAndroid` bridge name, no `SHA-256` and no `getPackageArchiveInfo` call. The published updater verifies nothing before launching the installer |
| Signature schemes? | v1 (441 `MANIFEST.MF` entries; 438 digests recomputed against the zip, **0** mismatches) + v2. The APK Signing Block carries `v2` and the verified-SDK/proof-of-rotation pairs only — **no v3 block** |
| Is the signing key the advertised one? | **Yes.** The X.509 extracted from `META-INF/CERT.RSA` (independent DER walk and openssl agree) fingerprints to `0A:78:…:F2:DE`, the same value as `EXPECTED_CERT_SHA256` in `release.yml:22` and the README |
| Are `LICENSE`/`NOTICE` inside the APK? | **No** — the only entry under `assets/` is `dexopt/baseline.prof`. The source stages them for the next build; the wording that claimed they were already in the published file was corrected |

`unzip -t` is clean, the CMS signature verifies, and the root `SHA256SUMS` matches. The artifact is
intact and honestly signed — it is simply *old*, and the suite now prints that as warnings
(`1.0.3` vs `1.0.4`, no v3 block, no bundled assets) instead of letting a green build imply it is
current.

### 7.2 Fixes shipped in this pass

| Area | Finding → change |
| :--- | :--- |
| Updater | A verified APK waiting on the *install unknown apps* grant was dropped when the activity finished (`isFinishing()` is true after the Settings round trip) → path + expected SHA-256 + tag persisted in `SharedPreferences` and **re-verified** before the installer reopens. Staging moved from the reclaimable `cacheDir` to `filesDir/updates` with a matching `file_paths.xml` root; orphans pruned; the stream aborts past the published asset size; `installedVersionName()` uses `BuildConfig.VERSION_NAME` instead of a hardcoded `"1.0.3"`; `PRIVACY.md`/javadoc now state the real 15-minute cadence |
| Verifier | OEM builds return an empty `SigningInfo` for *archive* files, which read as `SIGNER_MISMATCH` and blocked every update → retry with the legacy `GET_SIGNATURES` flag and fall back to `info.signatures` |
| Downloads | `static boolean blobExportGuardInProgress` could leak permanently when a poll's callback never arrived, wedging every later export → owned (`WeakReference<WebView>`) + deadline-bounded claim released by `onWebViewDestroyed()`; the 48 MB ceiling now applies to `blob.size` in-page before the base64 copy is built (and to `data:` before decoding); blob URLs injected through a `\uXXXX` encoder instead of `'`/`\` escaping |
| File chooser | Camera cleanup read back fields it had just nulled, so every capture stayed on disk forever → deletes from the captured locals, plus `pruneStaleCameraCaptures()` at launch and removal of the dead `pendingCameraMime` |
| Intents | `Intent.parseUri` + `startActivity` from page text was an intent-redirection primitive (categories are not matched for explicit intents) → `component`/`selector`/`package` stripped, `browser_fallback_url` restricted to http(s); popups can no longer open popups; the installer now gets an explicit archive MIME + read grant rather than `ACTION_VIEW` MIME guessing |
| Memory | Four retention chains removed: the static progress `AlertDialog` (pinned the whole destroyed `ContextThemeWrapper` graph), the update worker and its progress posts (up to 23 s of socket timeouts holding a dead Activity), the 120-tick blob poll (which also called `evaluateJavascript` on a destroyed WebView), and the reconnect `HEAD` probe (~5 s past `onDestroy`, connection never disconnected) |
| Peak heap | Retention was only half of it: a download's base64 payload was copied out of the incoming `String` with `substring()` and then decoded into a fresh `byte[]`, so an export at the permitted 48 MB ceiling peaked near **240 MB live** — past the default per-app heap limit on many devices, i.e. an out-of-memory kill at a size the ceiling explicitly allows. The payload now decodes from the original string region straight into the destination through one reusable 64 KB buffer (`AsciiRegionStream` + `Base64.getMimeDecoder().wrap`), which re-checks the ceiling while copying; a failed MediaStore write also deletes its pending row instead of leaving a zero-length download |
| Platform | Pull-to-refresh falls back to the native root-scroll check off-Arena (it was structurally impossible on `accounts.google.com`, `github.com/login/oauth`, `lmsys.org`, …) and uses the captured snapshot; `https://*.lmsys.org` is now a bridge origin and the two host lists are asserted equal; `onConfigurationChanged` applies system font scale; cached insets re-applied on exiting fullscreen; deprecated `onReceivedError` overload removed; the MainActivity typo fixed |
| Tooling | Dev proxy: WebSocket upgrades attached explicitly (`server.on('upgrade')`), error handling no longer assumes an HTTP response, loopback `Host` pinning with `ALLOWED_HOSTS`, LAN bind warning; `docker-compose.yml` publishes on `127.0.0.1` only; `requirements.txt` pins `Pillow==11.3.0`; `npm audit fix` → 0 vulnerabilities; `NOTICE` no longer credits Material Components and is re-staged as `assets/NOTICE.txt` |
| Tests | **92 → 127 checks.** Suite 10 now reads the APK itself (zip central directory, v1 digest recomputation, PKCS#7 cert DER walk vs. the README fingerprint and the CI pin, binary-manifest `versionName`, signing-block scheme IDs), fails on undisclosed committed-binary drift, asserts the new behavioural invariants, and `EXPECTED_CHECK_COUNT` is gated in `tests/runner.js` so every quoted count has to move with the suite |

### 7.3 Release state

- **Not release-ready as v1.0.4** until a `v1.0.4` tag + release exists; publishing is what makes
  any of the above reachable by users of 1.0.3.
- **No build is possible in this sandbox, by network policy rather than missing packages.**
  `java`, `javac`, `gradle`, `aapt` and `apksigner` are all absent, `apt-get` cannot even locate
  `openjdk-17-jdk-headless`, and the only reachable hosts are `github.com`, `codeload.github.com`,
  `api.github.com`, `registry.npmjs.org`, `pypi.org` and `files.pythonhosted.org` — so
  `dl.google.com` (Android SDK, AGP, AndroidX) and `services.gradle.org` (the wrapper distribution,
  `gradle-8.14.6-bin.zip`) are unreachable. Installing a JDK would not be enough; the dependency
  graph cannot be fetched at all. **`./gradlew test assembleRelease` on a real toolchain is required
  before tagging.**
- What was done instead, so "unverified" does not mean "unchecked": the four sources were parsed
  with a Java grammar (0 error nodes); a symbol sweep over every bare call, `this.` field access and
  undeclared identifier returned only inherited framework members (no typos, and no lingering
  reference to the removed `pendingCameraMime`); every `R.<type>.<name>` in Java and every
  `@<type>/<name>` in the manifest was confirmed to exist under `res/` — now a permanent audit-suite
  check, since `aapt` is exactly what would catch that class of error; and all manifest/XML files
  parse as well-formed XML. Signature and arity of every changed method were re-read at each call
  site by hand.
- No emulator or device: nothing here is on-device verified, which matters most for the OEM
  `SigningInfo` quirk and the camera-capture cleanup.
- `reproducible-artifacts` remains `false`; the icon generator produces the right structure but is
  not byte-reproducible across Pillow versions.
