<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="official_apple_touch_icon_dark.png">
    <source media="(prefers-color-scheme: light)" srcset="official_apple_touch_icon.png">
    <img alt="Arena.ai Official Logo" src="official_apple_touch_icon_dark.png" width="128" height="128">
  </picture>
</p>

<h1 align="center">Arena.ai Android Client (Unofficial)</h1>

<p align="center">
  <strong>An open-source, mobile-optimized native Android client for <a href="https://arena.ai">Arena (arena.ai)</a></strong>
</p>

<p align="center">
  <a href="https://github.com/Mr-Nobody-Anonymous/arena-ai-android/actions/workflows/build.yml"><img src="https://github.com/Mr-Nobody-Anonymous/arena-ai-android/actions/workflows/build.yml/badge.svg" alt="Android Build & CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg" alt="Platform"></a>
  <a href="android/"><img src="https://img.shields.io/badge/Package-ai.arena.mobile-purple.svg" alt="Package"></a>
  <a href="https://github.com/Mr-Nobody-Anonymous/arena-ai-android/releases/latest"><img src="https://img.shields.io/badge/Release-Latest-orange.svg" alt="Release"></a>
  <a href="tests/"><img src="https://img.shields.io/badge/Audit_Tests-123%2F123_Passing-success.svg" alt="Audit Tests"></a>
</p>

An open-source, high-performance native Android application for **[Arena (arena.ai)](https://arena.ai)** (formerly LMSYS Chatbot Arena). This application provides a full-screen, mobile-optimized experience for crowdsourced LLM evaluation, blind pairwise model battles, multimodal vision benchmarks, live Elo leaderboards, and direct model chat.

> [!IMPORTANT]
> **Disclaimer:** This is an independent, community-driven open-source project. It is **not affiliated with, maintained by, endorsed by, or sponsored by Arena, arena.ai, or LMSYS Org**. "Arena", "Chatbot Arena", and the Classical Ionic Pillar mark are the intellectual property and trademarks of Arena / arena.ai.

---

## 📑 Table of Contents

- [Overview & What the App Has](#overview--what-the-app-has)
- [How to Use the App](#how-to-use-the-app)
- [Detailed Feature Deep Dives](#detailed-feature-deep-dives)
  - [1. Smooth Scrolling & Gestures (No Accidental Reloads)](#1-smooth-scrolling--gestures-no-accidental-reloads)
  - [2. In-App Automated Self-Updater from GitHub Releases](#2-in-app-automated-self-updater-from-github-releases)
  - [3. Enhanced Multi-File & Image Upload Bridge](#3-enhanced-multi-file--image-upload-bridge)
  - [4. 5-Second Intelligent Auto-Reconnect Engine](#4-5-second-intelligent-auto-reconnect-engine)
  - [5. Authentic Adaptive Branding & Material You Support](#5-authentic-adaptive-branding--material-you-support)
  - [6. Multi-Scheme Cryptographic Signing (v1, v2, v3)](#6-multi-scheme-cryptographic-signing-v1-v2-v3)
  - [7. Client-Side Security & Privacy Hardening](#7-client-side-security--privacy-hardening)
  - [8. Native Android Platform UX](#8-native-android-platform-ux)
  - [9. Native Web Capability Bridge (No Extra UI)](#9-native-web-capability-bridge-no-extra-ui)
- [Download & Installation Guide](#download--installation-guide)
  - [Step-by-Step Installation](#step-by-step-installation)
  - [Troubleshooting Common Installation Issues](#troubleshooting-common-installation-issues)
- [Technical Specifications & Verification](#technical-specifications--verification)
- [Building from Source](#building-from-source)
- [Project Architecture & Directory Structure](#project-architecture--directory-structure)
- [Authentication Notes & Limitations](#authentication-notes--limitations)
- [Known Issues](#known-issues)
- [License & Attributions](#license--attributions)

---

## 🏛️ Overview & What the App Has

The application is engineered as a clean, single-codebase Android Gradle project (`ai.arena.mobile`) designed to run seamlessly across all modern Android versions (Android 8.0 Oreo through Android 15+).

| Capability | Current Implementation Status |
| :--- | :--- |
| **Connection Architecture** | **100% Standalone** direct HTTPS to `https://arena.ai` (no companion server, proxy, or LAN required) |
| **Android Version Support** | **Android 8.0 (API 26) through Android 15/16** (`minSdk 26`, `compileSdk`/`targetSdk` 35) |
| **Build Toolchain** | **Gradle 8.14 · AGP 8.13.2 · JDK 17 · R8 shrinking** with mapping files archived by CI |
| **Scrolling & Gestures** | **Smooth In-App Scrolling + page-aware pull-to-refresh**: the gesture only arms at the true top of the page (nested scrollers included), so scrolling up in chat never reloads or wipes conversations |
| **Self-Updater** | **Verified In-App GitHub Releases Updater**: checks on launch, on resume and after navigation (15-minute throttle, 5-minute retry after a failure), progress and cancel, "skip this version", zero permanent UI clutter |
| **File & Image Uploads** | **Multi-File Chooser (`ClipData`)**: Robust MIME type parsing for `.jpg`, `.png`, and images for Vision Arena, plus **direct camera capture** for `<input capture>` |
| **Downloads** | **Real DownloadManager downloads** with the WebView's session cookies, plus **`blob:` and `data:` exports** written to Downloads |
| **Web Capability Bridge** | **Native JS dialogs**, **fullscreen HTML5 video**, **before-unload guard**, **`intent://` deep links**, **Android share sheet** via `navigator.share` |
| **Resilience** | **WebView renderer recovery**, HTTP 5xx / TLS / offline handling with backoff auto-reconnect |
| **Self-Updater Integrity** | SHA-256 + package name + version code + signer certificate verified **before** the installer opens |
| **Branding & Iconography** | **Official Classical Ionic Pillar** vector & raster mipmaps across all screen densities (`mdpi`–`xxxhdpi`) |
| **Offline Resilience** | **5-Second Auto-Reconnect Engine** with AMOLED reconnect screen, live countdown & auto-retry |
| **Packaging & Signing** | **Full APK Signature Scheme v1, v2 & v3** with 4-byte zip alignment (`zipalign`) |
| **Security & Privacy** | **Zero Telemetry**, Geolocation disabled, File scheme access disabled, Cleartext traffic blocked |
| **Navigation & History** | **Predictive Back Navigation** (`OnBackPressedCallback`), strict Navigation Allow-List |
| **User Experience** | **Session Cookie Persistence**, **OAuth Popup Windows** (`onCreateWindow`), **AMOLED Theme** |

---

## 🎮 How to Use the App

Once installed on your Android device, the app gives you the full Arena benchmarking experience:

1. **Pairwise Blind Battles (Chatbot Arena):**
   - Tap into the **Arena (battle)** tab.
   - Enter your prompt or coding problem into the text box.
   - Two state-of-the-art LLMs (e.g., GPT-4o, Claude 3.5 Sonnet, Gemini 1.5 Pro, Llama 3.1) generate responses anonymously side-by-side.
   - Vote on which model provided the superior answer: **Model A is better**, **Model B is better**, **Tie**, or **Both are bad**.
   - After voting, model identities are revealed and your vote contributes to LMSYS's live global Elo leaderboard.

2. **Multimodal Vision Battles:**
   - Tap into the **Vision** tab.
   - Use the native file picker to upload one or more benchmark images (diagrams, photos, UI screenshots).
   - Scroll up and down comfortably to review your uploaded images and battle responses without any accidental page reloads.

3. **Coding & Hard Prompts Arenas:**
   - Benchmark complex algorithmic problems, code refactoring, logic puzzles, and difficult multi-step queries under specialized categories.

4. **Leaderboards & Model Rankings:**
   - Browse the live, crowd-sourced leaderboard table to review model Elo ratings, 95% confidence intervals, and category rankings (Overall, Coding, Hard Prompts, Vision, French, etc.).

5. **Direct Model Chat:**
   - Switch to **Direct Chat** mode when you want to interact directly with a specific LLM without blind voting.

6. **Automatic In-App Updates:**
   - Whenever an update or fix is released on GitHub, the app detects it automatically in the background and asks if you would like to download and install the update.

---

## 🔍 Detailed Feature Deep Dives

### 1. Smooth Scrolling & Pull-to-Refresh (No Accidental Reloads)

Arena.ai renders chats, battles and leaderboards inside nested scrollable containers (`overflow-y: scroll/auto`). The Android WebView's root window therefore stays at scroll position `0` even while a conversation is scrolled deep into history, so a naive pull-to-refresh wrapper mistook ordinary "scroll up through a battle" drags for a refresh, reloading the page and wiping the conversation and uploads.

The gesture is now page-aware instead of disabled:

- **The page decides.** A small shim (`VIEWPORT_SHIM_JS`, injected only on Arena origins) tracks the root document *and* every visible nested scroller, and posts only state changes over the origin-locked `arenaAndroid` bridge.
- **The gesture is armed only at the true top.** `swipeRefreshLayout.setOnChildScrollUpCallback(...)` consults `canPullToRefresh()`, which requires `pageAtTop && !pageLoading && getScrollY() <= 0`. Scrolling up inside a chat leaves the gesture disarmed, so the old accidental-reload bug cannot come back.
- **Pull down at the top to reload.** The gesture reloads the current page (`view.reload()`, preserving the URL and in-page state such as the open chat) and shows the standard spinner until the page finishes. On the reconnect screen the same gesture retries the connection immediately.
- **Safe fallback.** If the WebView is too old for the WebMessage bridge, the gesture simply stays disabled rather than risking an accidental reload.

---

### 2. In-App Automated Self-Updater from GitHub Releases

The application includes an integrated background updater (`AppUpdateManager`) that keeps the app up-to-date with the latest community fixes and releases from GitHub:

- **Zero Additional UI Clutter:** The updater does not add any intrusive toolbars, banners, or floating buttons to the interface. The app remains clean and full-screen.
- **Automated Background Verification:** The updater queries `https://api.github.com/repos/Mr-Nobody-Anonymous/arena-ai-android/releases/latest` on app start, whenever the app returns to the foreground, and after a page finishes loading. Successful checks are throttled to one per 15 minutes (a fraction of GitHub's 60/hour unauthenticated quota); a failed check is retried after 5 minutes instead of being silenced for the whole interval.
- **Semantic Version Checking:** Compares the remote release tag (e.g., `v1.0.4`) against the installed version name, ignores drafts and pre-releases, and remembers the version the user skipped.
- **No nagging:** the same release is offered at most once per 12 hours even across restarts, and only once per app process.
- **Native User Prompt:** When a newer release is detected, the app presents a clean native `AlertDialog`:
  > **Update Available**  
  > *A new version of Arena AI (v1.0.2) is available with fixes and updates.*  
  > *Would you like to download and install this update?*  
  > **[Update Now]** / **[Later]**
- **Seamless Download & Package Installation:**
  - Upon user confirmation, downloads `ArenaAI.apk` directly into the app's secure cache directory (`updates/ArenaAI-update.apk`).
  - Follows HTTP redirects (e.g., AWS S3 release assets).
  - Uses `androidx.core.content.FileProvider` and launches `Intent(Intent.ACTION_VIEW)` with MIME type `application/vnd.android.package-archive` to trigger Android's system package installer.
  - Automatically guides the user if "Install unknown apps" permission is required on Android 8.0+.

---

### 3. Enhanced Multi-File & Image Upload Bridge

Uploading images for Arena Vision battles is fully optimized:
- **Intelligent MIME Parsing:** Parses `accept` attributes, supporting comma-separated formats (`image/jpeg,image/png`), extension formats (`.jpg,.png`), and wildcard image selection (`image/*`).
- **Multi-File Selection:** Fully supports `ClipData` multi-selection so multiple benchmark images can be uploaded simultaneously.
- **Camera Capture:** When a page sets `capture` on a file input (for example a Vision prompt photographed on the spot), the chooser offers the system camera alongside the file picker. The capture is written through the app's `FileProvider`.
- **Cancellation & Failure Safety:** Guarantees that `filePathCallback.onReceiveValue(null)` is called if file selection is cancelled or interrupted, preventing the WebView from locking up.
- **Persistent State:** Uploaded images and prompts remain intact because scroll gestures no longer trigger accidental page reloads.

---

### 4. 5-Second Intelligent Auto-Reconnect Engine

Mobile network connectivity often drops when transitioning between Wi-Fi and mobile data or passing through dead zones. Rather than displaying an ugly default Chromium error screen (`net::ERR_INTERNET_DISCONNECTED`), this client features an integrated auto-reconnect engine:

- **Error Interception:** When WebView detects network interruption (`onReceivedError`), it immediately renders an AMOLED `#000000` reconnect view.
- **Visual Design:** Displays the authentic Classical Ionic Pillar mark, an animated spinning circular loader, and a live countdown timer (`Checking in 5s...`).
- **Background Health Probes:** A non-blocking background thread pool (`networkExecutor`) executes lightweight `HEAD` requests to `https://arena.ai` via `HttpURLConnection` every 5 seconds.
- **Zero-Touch Auto-Recovery:** The instant network connectivity is restored (e.g. Wi-Fi re-established or Airplane Mode turned off), the app automatically cancels the timer, dismisses the error view, and loads `https://arena.ai` seamlessly.
- **Manual Override:** A prominent **"Retry Now"** button is also provided for immediate manual reconnection.

---

### 5. Authentic Adaptive Branding & Material You Support

The application delivers a first-class, authentic home screen presence matching modern Android design guidelines:

- **Adaptive Vector Drawables:**
  - Foreground (`@drawable/ic_launcher_foreground.xml`): Vector representation of the Classical Ionic Pillar mark centered on a 108dp adaptive canvas.
  - Background (`@drawable/ic_launcher_background.xml`): Solid AMOLED `#000000` deep black.
  - Themed Monochrome (`@drawable/ic_launcher_monochrome.xml`): Android 13+ (API 33+) dynamic Material You icon that automatically tints to match the user's wallpaper color palette.
- **Dual Adaptive XML Manifestations:** Configured in `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
- **Comprehensive Density Raster Fallbacks:** Includes pre-rendered 32-bit RGBA icons across all standard Android density buckets: `mdpi` (48x48), `hdpi` (72x72), `xhdpi` (96x96), `xxhdpi` (144x144), and `xxxhdpi` (192x192).

---

### 6. Multi-Scheme Cryptographic Signing (v1, v2, v3)

- **Multi-Scheme Signing:** Builds from `android/` enable **APK Signature Scheme v1** (JAR signing), **v2** and **v3** (`v1SigningEnabled`, `v2SigningEnabled`, `enableV3Signing` in `android/app/build.gradle`).
- **Stable Release Key:** The release keystore is supplied through `ARENA_RELEASE_STORE_FILE` / `ARENA_RELEASE_STORE_PASSWORD` / `ARENA_RELEASE_KEY_ALIAS` / `ARENA_RELEASE_KEY_PASSWORD` (Gradle properties or environment variables) and GitHub Actions secrets. CI never generates a signing key. Without those values the build warns loudly and falls back to the debug key, which must never be distributed.
- **Android 11–15 Compatible:** v2 and v3 signatures eliminate the *"App not installed as package appears to be invalid"* error on Android 11 through 15.
- **4-Byte Zip Alignment (`zipalign`):** Every uncompressed archive entry (including `resources.arsc`) is 4-byte aligned. `scripts/verify-apk.sh` re-checks this on every build.
- **Historical Artifact:** The `ArenaAI.apk` committed at the repository root is the published **v1.0.3** binary (v1 + v2 signatures). Every build produced by `.github/workflows/release.yml` includes v3.

---

### 7. Client-Side Security & Privacy Hardening

- **Zero Telemetry or Analytics:** No third-party tracking SDKs (Firebase, Adjust, Mixpanel, Google Analytics, Sentry) are included in the binary.
- **HTML5 Geolocation Disabled:** `WebSettings.setGeolocationEnabled(false)` prevents web scripts from querying physical device GPS coordinates.
- **Local File System Protection:** `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setAllowFileAccessFromFileURLs(false)`, and `setAllowUniversalAccessFromFileURLs(false)` prevent web content from reading private device files via `file://` or content URIs.
- **Cleartext Traffic Disabled:** `android:usesCleartextTraffic="false"` strictly forbids non-HTTPS HTTP connections at the Android OS network security layer.
- **Application Backup Disabled:** `android:allowBackup="false"` prevents extraction of session cookies and tokens via Android Debug Bridge (`adb backup`).
- **Navigation Security Allow-List:** Embedded WebView navigation is strictly confined to Arena domains (`arena.ai`, `lmarena.ai`, `lmsys.org`) and the exact OAuth endpoints that need to stay in-app (`accounts.google.com`, `github.com`, `appleid.apple.com`, `login.microsoftonline.com`, `login.live.com`, `discord.com`, `huggingface.co`, `*.auth0.com`). Broad consumer domains such as `google.com` are **not** allowed: a signed-out Google page or an attacker-crafted one would otherwise render inside the trusted shell. Any external link is handed off to the user's default system browser via `Intent.ACTION_VIEW`.

---

### 8. Native Android Platform UX

- **Predictive Back Navigation:** Migrated from deprecated `onBackPressed()` to AndroidX `OnBackPressedCallback`, preventing navigation glitches on Android 14+ and Android 15/16 (API 34–36).
- **Session & Cookie Persistence:** Explicit calls to `CookieManager.getInstance().flush()` during `onPageFinished`, `onPause`, `onStop`, and `onDestroy` guarantee login cookies survive process restarts.
- **Secondary Window & OAuth Support:** `WebChromeClient.onCreateWindow` creates dedicated fullscreen dialog WebViews with third-party cookie support for popup authentication dialogs.
- **Download Management:** A real `DownloadListener` implementation (see section 9) forwards the WebView's cookies and user agent to Android's `DownloadManager`, resolves `blob:`/`data:` exports, and writes them to Downloads. It is no longer a bare `ACTION_VIEW` hand-off.

---

### 9. Native Web Capability Bridge (No Extra UI)

The app deliberately ships **no navigation bar, toolbar, banner, floating button or menu of its own**. Everything the user sees is the Arena web interface. Native code only fills gaps a bare WebView cannot cover, and it always uses system surfaces (dialogs, share sheet, notification shade, installer) rather than app chrome:

| Web capability | Native behaviour |
| :--- | :--- |
| `<input type="file">` / `capture` | Multi-select file picker plus optional camera capture through the system camera app |
| `alert()` / `confirm()` / `prompt()` | Native `AlertDialog` equivalents (a default WebView silently drops these) |
| `beforeunload` | Confirmation dialog so back navigation cannot silently discard a battle |
| Pull-to-refresh | Arms only when the document *and* every visible nested scroller are at the top; reloads the current page, retries when offline |
| `<video>` fullscreen | `onShowCustomView` / `onHideCustomView` overlay, with back-button exit |
| `navigator.share()` | Origin-locked `WebMessage` bridge to the Android share sheet (`addWebMessageListener`) |
| `blob:` / `data:` download links | Resolved inside the page and written to Downloads via `MediaStore` |
| HTTPS downloads | `DownloadManager` with Arena cookies, progress in the notification shade |
| `window.open()` | OAuth/SSO hosts stay in a tracked popup; ordinary links open in the main view |
| `intent://` / `mailto:` / `tel:` | Parsed and opened by the matching system app, with `browser_fallback_url` support |
| Renderer crash | WebView is destroyed and recreated at the last URL instead of taking the app down |
| HTTP 5xx / TLS failure / offline | AMOLED reconnect screen with native health probes and backoff |

Nothing in this list adds a permanent control to the Arena UI; all of it is either invisible or a transient system dialog.

---

## 📲 Download & Installation Guide

### Step-by-Step Installation

1. **Download the Verified Release APK:**
   Download the release APK directly to your phone:  
   👉 **[Download the latest ArenaAI.apk](https://github.com/Mr-Nobody-Anonymous/arena-ai-android/releases/latest/download/ArenaAI.apk)**
2. **Allow Installation from Unknown Sources:**
   - Tap the downloaded `ArenaAI.apk` in Chrome or your phone's **Files / Downloads** app.
   - If prompted *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** and turn on **Allow from this source**.
3. **Complete Installation:**
   - Tap **Install** (or **Update** if updating from an existing install).
4. **Launch Arena:**
   - Open **Arena AI** from your home screen or app drawer.
   - All subsequent updates can now be installed directly inside the app using the automated self-updater!

---

### Troubleshooting Common Installation Issues

| Issue / Error | Cause | Solution |
| :--- | :--- | :--- |
| **"App not installed as package appears to be invalid"** | An older APK built with a mismatched signature key is installed on the device. | Uninstall the old version from your device first, then install the new APK. |
| **"Install Blocked by Play Protect"** | The APK is an open-source community release built outside Google Play. | Tap **More details** and select **Install anyway**. |
| **"Parse error: Problem parsing package"** | Download was incomplete or corrupted, or device is below Android 8.0. | Verify downloaded file size and re-download if necessary. |
| **"No internet connection detected" screen on launch** | Device is in Airplane mode or lacks an active Wi-Fi / cellular data connection. | Connect to Wi-Fi or mobile data; the app will auto-reconnect within 5 seconds. |

---

## 🔐 Technical Specifications & Verification (committed v1.0.3 artifact)

> Source on this branch is **v1.0.4 (versionCode 5)**. The table below describes the `ArenaAI.apk` binary committed at the repository root, which is the published v1.0.3 build; CI-produced v1.0.4 APKs are available from the releases page.

| Attribute | Specification |
| :--- | :--- |
| **File Name** | `ArenaAI.apk` |
| **File Size** | `2,825,952 bytes` (~2.83 MB) |
| **SHA-256 Checksum** | `6aef57e72714dbd8763cacb0aa2d75d929118f13d38dd619cd6426cd82b15db8` |
| **Package ID** | `ai.arena.mobile` |
| **Version** | `1.0.3 (versionCode 4)` |
| **Min SDK** | `API 26 (Android 8.0 Oreo)` |
| **Target SDK** | `API 34 (Android 14)` for this v1.0.3 binary; the source on `main` targets `API 35` |
| **Zip Alignment** | Verified 4-byte aligned (all stored archive entries) |
| **Signature Schemes** | v1.0.3 artifact: v1 (JAR) + v2 (APK Signature Block). Builds from current source: v1 + v2 + v3 |

### Signing Certificate Fingerprints
```text
Owner / Issuer: CN=Arena Mobile Community, OU=Open Source, O=Community, L=San Francisco, ST=CA, C=US
Algorithm:      2048-bit RSA with SHA256withRSA
Validity:       October 12, 2026 to February 27, 2054
SHA-256:        0A:78:95:E6:52:C7:F1:9C:58:8A:DD:1A:B8:40:B4:C5:EE:32:EA:C1:64:55:89:27:0F:34:65:05:99:D3:F2:DE
SHA-1:          DA:EA:97:45:CC:72:1D:E6:F4:D1:8A:A8:DA:43:53:0A:E3:86:BA:A4
```

### Verify Your Download

```bash
# 1. SHA-256 must match the value above and the SHA256SUMS asset
sha256sum ArenaAI.apk        # macOS: shasum -a 256 ArenaAI.apk

# 2. Signing certificate must match the fingerprint above
apksigner verify --print-certs ArenaAI.apk

# 3. Package, version and permissions
aapt dump badging ArenaAI.apk | head -20
```

`scripts/verify-apk.sh` runs all three checks plus 4-byte alignment in one step. Release managers must update the fingerprint table whenever the signing key changes.

---

## 🛠️ Building from Source

To compile the Android client locally:
- **JDK 17** or newer (AGP 8.13 / Gradle 8.14)
- **Android SDK** with Platform API 35 and Build Tools installed

```bash
# 1. Clone the repository and install the test harness
git clone https://github.com/Mr-Nobody-Anonymous/arena-ai-android.git
cd arena-ai-android
npm ci

# 2. Run the Android unit tests and the audit suite
(cd android && ./gradlew test)
npm test              # or: ./test.sh, which also runs Gradle when a JDK is present

# 3. Build a debug APK
(cd android && ./gradlew assembleDebug)

# 4. Build a signed release APK
#    Export the four ARENA_RELEASE_* variables documented in android/app/build.gradle
#    first. Without them the build warns and falls back to the debug key.
(cd android && ./gradlew assembleRelease)

# 5. Verify signatures, v1/v2/v3 schemes and 4-byte alignment
scripts/verify-apk.sh android/app/build/outputs/apk/release/app-release.apk
```

### Running the development proxy

```bash
./start.sh            # or: docker compose up --build
```

Neither is required by the app: the APK connects straight to `https://arena.ai`.

Environment knobs for the proxy:

| Variable | Default | Effect |
| :--- | :--- | :--- |
| `PORT` | `3000` | Port the proxy listens on |
| `HOST` | `0.0.0.0` | Bind address. The default exists so a phone on the LAN can reach it, and the proxy prints a warning whenever it is not loopback — use `HOST=127.0.0.1` plus an SSH tunnel or an mDNS/VPN address if you do not want it open to the network |
| `ALLOWED_HOSTS` | loopback only | Extra `Host` headers to accept while bound to loopback (comma-separated, e.g. `ALLOWED_HOSTS=proxy.local,192.168.1.20:3000`). Requests with any other `Host` get **403**, which is what stops a DNS-rebinding attack against a developer machine. Requests bound to a LAN address are not pinned, because the QR-code flow needs arbitrary host:port |

`docker compose` publishes `127.0.0.1:3000:3000`, so a containerised run is loopback-only unless you
change that mapping.

### Release process

Building, verifying and publishing a signed release is documented step by step in
[`RELEASE.md`](RELEASE.md), including the one-time `release` environment secrets and the
pre-flight `./scripts/release-check.sh`.

### Automated Compliance Test Suite
The repository ships an automated audit suite (123 checks) covering architecture, security
hardening, the native capability bridge, update integrity, release engineering, the development
server endpoints and the shipped APK **artifact itself** — parsed from bytes rather than from build
files: its SHA-256 against `SHA256SUMS`, every v1 (JAR) digest recomputed against the archive, the
signing certificate extracted from `META-INF/CERT.RSA` and compared with both the README
fingerprint and the `release.yml` pin, the APK Signing Block's scheme IDs (v2 vs. v3), the
`versionName` inside the binary AndroidManifest, and whether the committed binary matches the
source or is explicitly *disclosed* as a historical artifact. What the published APK is missing
relative to the source is printed as a loud warning rather than hidden behind a green check; the
total the documents quote is gated in `tests/runner.js`, so a stale count fails the run.

```bash
npm test
```

---

## 📁 Project Architecture & Directory Structure

```text
arena-ai-android/
├── .github/
│   ├── workflows/build.yml     # CI: unit tests, lint, audit suite, signed build, APK verification
│   ├── workflows/release.yml   # Tag-triggered: signed release, verification, GitHub Release upload
│   ├── workflows/codeql.yml    # CodeQL analysis for Java/Android and JavaScript
│   ├── CODEOWNERS              # Review ownership
│   ├── dependabot.yml          # Automated dependency security tracking (npm, gradle, actions)
│   ├── ISSUE_TEMPLATE/         # Bug report, feature request and contact links
│   └── PULL_REQUEST_TEMPLATE.md# Pull request contribution template
├── android/                    # Single source of truth Android application
│   ├── app/
│   │   ├── build.gradle        # App config: ai.arena.mobile, compileSdk/targetSdk 35, R8, v1/v2/v3 signing
│   │   ├── proguard-rules.pro  # R8 / ProGuard optimization & keep rules
│   │   └── src/
│   │       ├── main/
│   │       │   ├── AndroidManifest.xml # Permissions, FileProvider, camera query, backup rules
│   │       │   ├── assets/             # LICENSE.txt and NOTICE.txt bundled for Apache-2.0
│   │       │   ├── java/ai/arena/mobile/
│   │       │   │   ├── MainActivity.java      # WebView shell + native capability bridge
│   │       │   │   ├── DownloadHandler.java   # DownloadManager, blob: and data: downloads
│   │       │   │   ├── AppUpdateManager.java  # Throttled, cancellable, verified self-updater
│   │       │   │   └── UpdateVerifier.java    # SHA-256, package, version and signer checks
│   │       │   └── res/
│   │       │       ├── drawable/             # ic_launcher_background, foreground, monochrome
│   │       │       ├── mipmap-anydpi-v26/    # Adaptive icon XML definitions
│   │       │       ├── mipmap-[m|h|xh|xxh|xxxh]dpi/ # Density raster PNGs
│   │       │       ├── values/               # strings.xml, themes.xml
│   │       │       └── xml/                  # file_paths.xml, data_extraction_rules.xml
│   │       └── test/                         # Unit tests (NavigationPolicyTest, AppUpdateManagerTest)
│   ├── gradle/wrapper/         # Gradle 8.14 wrapper binaries and configuration
│   ├── gradle.properties       # AndroidX configuration
│   └── gradlew, gradlew.bat    # Cross-platform Gradle wrapper execution scripts
├── scripts/verify-apk.sh       # apksigner + zipalign + aapt + sha256 in one step
├── scripts/release-check.sh    # Pre-flight: every version string must agree before tagging
├── start.sh / test.sh          # Unix equivalents of start.bat / test.bat (npm ci first)
├── Dockerfile / docker-compose.yml # Containerised development proxy
├── RELEASE.md                  # Release contract: secrets, tagging, verification, rollback
├── tests/                      # Automated audit suite (architecture, security, artifact, API)
├── server.js                   # Optional local reverse proxy (development inspection only)
├── process_official_icon.py    # Regenerates launcher icons (requires requirements.txt)
├── ArenaAI.apk                 # Published v1.0.3 binary (see "Historical Artifact" above)
├── SHA256SUMS                  # Cryptographic checksum manifest
├── ANALYSIS_REPORT.md          # Technical audit remediation report
├── CHANGELOG.md                # Detailed release changelog
├── CONTRIBUTING.md             # Contribution guidelines
├── CODE_OF_CONDUCT.md          # Contributor covenant + enforcement contact
├── CITATION.cff                # Citation metadata
├── .editorconfig / .nvmrc      # Editor and Node version pins
├── LICENSE                     # Apache License 2.0
├── NOTICE                      # Legal & trademark notices
├── PRIVACY.md                  # Comprehensive privacy statement
└── SECURITY.md                 # Vulnerability disclosure policy
```

---

## 🔑 Authentication Notes & Limitations

- **Email & Password Login:** Fully supported and preserved across app sessions via `CookieManager.getInstance().flush()`.
- **Secondary OAuth Dialogs:** Popup windows spawned by authentication providers are cleanly handled inside modal dialog WebViews via `WebChromeClient.onCreateWindow`.
- **Google Sign-In Policy:** Google enforces strict server-side policies restricting OAuth from embedded WebViews (`Error 403: disallowed_useragent`). The app strips the `; wv` marker from the WebView User-Agent to optimize compatibility. Users encountering Google OAuth restrictions are advised to log in using Email/Password or complete authentication through their default web browser.

---

## 🧷 Known Issues

Honest limitations of the current build — none of them adds UI, all are tracked.

| Area | Limitation | Workaround |
| :--- | :--- | :--- |
| Google sign-in | Google blocks OAuth inside embedded WebViews (`disallowed_useragent`). The UA shim helps but cannot guarantee it | Use email/password, or sign in through the browser |
| Voice / camera web APIs | No `RECORD_AUDIO` / `CAMERA` permission is declared, so `getUserMedia` prompts are denied. `capture` uploads use the **system camera app** instead | Upload a recorded file, or use the app's file picker |
| Incoming links | No deep links: an `arena.ai` link from another app opens the browser. Doing this safely requires `assetlinks.json` on `arena.ai`, which this project does not control | Use the in-app share sheet |
| Pull-to-refresh | On Arena pages it arms only after the page reports it is at the top, so a page that moves its scrollers into shadow DOM or an iframe keeps the gesture disabled (never the reverse). Non-Arena allow-listed pages use the ordinary root-scroll check | Pull again after the page settles, or reopen the app |
| Updates with no checksum | A release without `SHA256SUMS` and without a GitHub asset digest is refused by design (`MISSING_HASH`) | Always publish through `release.yml` |
| Update checks | At most one per 15 minutes; failures retry after 5 minutes. A shared/rate-limited IP can make checks fail quietly | Wait, or download the APK from the releases page |
| Large `blob:` exports | In-page export is capped at 48 MB of blob and 30 s; the limit is applied before the base64 copy is built, so an oversized export fails cleanly instead of pressuring the renderer | Use a smaller export |
| OAuth popups | Tracked and torn down correctly, but some providers still refuse embedded WebViews | Sign in via the browser |
| Nested popups | A popup window cannot open a popup of its own (`onCreateWindow` returns false inside a popup), so a provider that chains `window.open` twice stops at the second step | Complete that sign-in in the browser |
| Update prompt cadence | `promptShownThisSession` means one prompt per app process: **Later** is honoured until you restart, not until the next page load | Restart the app, or install from the releases page |
| Toolchain | `compileSdk`/`targetSdk` 35, minSdk 26. Android 15 edge-to-edge is handled with inset padding; older devices are unaffected | — |

Not verifiable from CI: on-device behaviour of the WebView page itself (camera handoff,
DownloadManager with live cookies, installer flow). Those paths are exercised manually
before a release.

---

## 📄 License & Attributions

This project is licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for complete details.

### Trademarks & Attributions
- "Arena", "Chatbot Arena", and the Classical Ionic Pillar mark are trademarks and intellectual property of **Arena / arena.ai** (formerly LMSYS Org).
- Android is a trademark of Google LLC.
- Third-party components and license notices are documented in the [NOTICE](NOTICE) file.
