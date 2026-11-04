# Changelog

All notable changes to the Arena.ai Android Client project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Fixed — download pipeline

- **A destroyed page can no longer wedge every later `blob:` export.** The in-page handshake
  guard became an owned, expiring claim (`WeakReference<WebView>` + deadline) instead of a bare
  `static boolean`, and `DownloadHandler.onWebViewDestroyed()` releases it from
  `onDestroy()` and from renderer-crash recovery. Previously a poll whose callback never arrived
  left the flag set for the life of the process ("A download is already in progress" forever).
- **The 48 MB export ceiling is now enforced inside the page**, on `blob.size` before
  `readAsDataURL()`, instead of on the Java side after the base64 copy had already been built in
  the renderer and copied across JNI as a `String` — i.e. after the memory spike it existed to
  prevent. `data:` downloads are capped before decoding too.
- **Blob URLs are injected as a properly escaped JavaScript string literal**
  (`toJavaScriptStringLiteral`), replacing hand-rolled `'`/`\` escaping that could produce a
  script which fails to compile and silently cancels the export.
- **A permitted download no longer needs several times its own size in heap.** The base64 payload
  used to be copied out of the incoming `String` with `substring()` and then decoded into a fresh
  `byte[]`, so a 48 MB export peaked at roughly 240 MB of live allocation — past the default
  per-app limit on many devices, i.e. an out-of-memory kill at a size the ceiling explicitly
  allows. The payload is now decoded straight from the region of the original string
  (`AsciiRegionStream` + `Base64.getMimeDecoder().wrap`) into the destination through one reusable
  64 KB buffer, which also enforces the ceiling while copying.
- **A failed MediaStore write no longer leaves a pending row behind.** If opening the stream or
  copying throws, the half-created `Downloads` entry is deleted instead of staying as a
  zero-length file the user never asked to keep.

### Fixed — self-updater

- **Deferred installs survive the process being killed.** A verified APK waiting for the
  *install unknown apps* grant is recorded in `SharedPreferences` (path, expected SHA-256, tag)
  and re-verified through `UpdateVerifier` before the installer reopens, so the round trip
  through Settings no longer silently discards the download the user asked for.
- **A waiting APK now lives in `filesDir/updates`, not `cacheDir`.** The cache is reclaimable,
  which was exactly how the deferred file could vanish mid-hand-off; `file_paths.xml` gained the
  matching `<files-path>` root and dropped an unused, over-broad `external-files-path` one.
- **No orphaned downloads.** Leftover `.part` files and superseded releases are pruned at the
  start of each download, an unfinished-but-verified file is no longer stranded when the
  activity disappears, and the stream is aborted if it exceeds the published asset size.
- **`installedVersionName()` no longer falls back to a hardcoded `"1.0.3"`**; it uses
  `BuildConfig.VERSION_NAME` and returns `null` (→ no prompt) rather than comparing every future
  release against one fixed version.
- **The class documentation and `PRIVACY.md` state the real check interval** (15 minutes, 5-minute
  retry after a failure) instead of the 24 hours left over from an earlier revision.

### Fixed — file chooser

- **Camera captures are actually cleaned up.** `deliverFileChooserResult()` nulled
  `pendingCameraUri`/`pendingCameraFile` and *then* asked the cleanup helper to delete, which
  re-read the fields it had just cleared and so never deleted anything; the delete now runs on
  the captured locals, and `pruneStaleCameraCaptures()` reclaims stills from earlier sessions at
  launch. The unused `pendingCameraMime` field is gone.

### Fixed — memory

- **Background work no longer pins a destroyed Activity.** The update check (up to 23 s of socket
  timeouts), the download worker, its progress messages and the reconnect HEAD probe all reached
  the activity strongly, keeping a finished screen — WebView and bitmap heap included —
  unreachable for the duration. They now go through `WeakReference` with a liveness re-check on
  the main thread, the static progress-dialog handle is weak, and the queued blob poll is a
  removable token rather than an anonymous chain in the message queue.

### Fixed — security

- **`intent://` links can no longer name their own target.** `openExternal()` parsed the URI and
  launched it with only `CATEGORY_BROWSABLE` added, which is the textbook WebView
  intent-redirection primitive (categories are not matched for explicit intents, so
  `component=` from page content reached any exported activity). Component, selector and package
  are now stripped, leaving an implicit browsable intent, and `browser_fallback_url` is accepted
  only over http(s) so a failed deep link cannot smuggle in a second intent or a `file://` path.

### Fixed — platform behaviour

- **`getPackageArchiveInfo()` quirks no longer block all updates.** `SigningInfo` comes back empty
  for *archive* files on several OEM builds; the verifier now retries with the legacy
  `GET_SIGNATURES` flag and falls back to `info.signatures` before concluding `SIGNER_MISMATCH`.
- **Pull-to-refresh works on non-Arena allow-listed pages.** It previously required a shim state
  that is only injected on Arena origins (and only when the WebView supports the WebMessage
  bridge), so OAuth/docs pages had no refresh gesture at all; those pages now use the native
  root-scroll check while Arena keeps the page-reported one.
- **Bridge origins cover every shimmed host.** `https://*.lmsys.org` was registered while the
  shims go to all of `lmsys.org`, so a `*.lmsys.org` page got shims that no-op'd against a
  missing `arenaAndroid` object; the two lists are now asserted to agree.
- **System font size changes take effect without a restart** (`onConfigurationChanged` recomputes
  `textZoom`; `fontScale` is in `android:configChanges`, so nothing else would notice).
- **Fullscreen video no longer strands the page under the status bar**: the cached insets are
  re-applied on exit instead of relying on `requestApplyInsets()` redispatching.
- Removed the deprecated `onReceivedError(WebView, int, String, String)` override, which cannot
  fire at `minSdk 26`.

### Changed — tests, tooling and documents

- **Audit suite: 92 → 127 checks, and Suite 10 now reads the artifact instead of the build
  files.** It parses the committed APK's zip central directory, recomputes every v1 (JAR) digest,
  extracts the signing certificate from the PKCS#7 blob and compares its SHA-256 against the
  README fingerprint *and* the `release.yml` pin, reads the `versionName` out of the binary
  manifest, and walks the APK Signing Block for v2/v3/v3.1 pair IDs. A build file can claim
  anything; these are the checks that describe what was shipped.
- **Committed-binary drift can no longer pass silently.** The suite fails unless the APK either
  matches the source version or is explicitly disclosed as a historical artifact, and prints
  warnings for what the published file lacks (current version, v3 block, bundled
  `LICENSE`/`NOTICE`). `EXPECTED_CHECK_COUNT` is gated in `tests/runner.js`, so the counts quoted
  in `README.md`, `RELEASE.md` and `test.sh` have to move with the suite.
- The `Legal` check is renamed to what it actually inspects (staged assets, not the APK) and a
  new `NOTICE` check keeps the dependency list honest — it no longer credits Material Components,
  which the app does not link.
- `NOTICE`/`assets/NOTICE.txt` stay byte-identical; `requirements.txt` pins `Pillow==11.3.0`
  because the committed launcher rasters are this script's output and resampling is not stable
  across Pillow majors.
- Dev proxy: `body-parser`/`qs` updated (both advisories now clear, `npm audit` reports 0), a
  Host-header pin blocks DNS rebinding while bound to loopback, WebSocket upgrades are attached
  explicitly (they previously needed an unrelated HTTP request first), proxy error handling no
  longer assumes an HTTP response exists for a socket upgrade, and `docker-compose.yml` publishes
  on `127.0.0.1` only. A LAN bind now prints an explicit warning.
- Corrected stale toolchain/version claims in `CHANGELOG` context, `REPO_GAP_ANALYSIS.md` and
  `README.md` (Gradle **8.14.6** / AGP **8.13.2**, lock at `1.0.4`, 48 strings).
- **The suite resolves resource names itself.** `aapt` does it at build time and this environment
  has no JVM, so a new check re-implements the resolution: every `R.<type>.<name>` in the four Java
  sources and every `@<type>/<name>` in the manifest has to exist under `res/`. Verified to fail on
  a deliberately dangling reference before it was kept.

## [1.0.4] - 2026-10-18

### Changed — Build, SDK and hardening

- **Toolchain brought up to date:** Gradle 8.2 → **8.9**, AGP 8.2.2 → **8.7.3**,
  `compileSdk`/`targetSdk` 34 → **35** (minSdk stays 26). Android 15's enforced edge-to-edge
  layout is handled by padding the shell with `systemBars` + `displayCutout` insets, so the
  web page keeps exactly the usable area it had before.
- **R8 is enabled for release builds:** `minifyEnabled true` and `shrinkResources true`, with
  broad keep rules (every public member of `ai.arena.mobile`, WebView callbacks, AndroidX
  components referenced from the manifest). CI uploads `mapping.txt` for deobfuscation.
- **Navigation allow-list narrowed:** `google.com` and `live.com` were removed; only
  `accounts.google.com` and `login.live.com` remain for those providers. Broad consumer
  domains can no longer render inside the trusted shell.

### Changed — Refresh gesture and update discovery

- **Pull-to-refresh is back, without the old reload bug:** the gesture is armed only when the
  page reports that the document *and* every visible nested scroller are at the top
  (`VIEWPORT_SHIM_JS` → origin-locked bridge → `canPullToRefresh()`). Scrolling up through a
  battle no longer reloads the page; pulling down at the top reloads the current URL, and on the
  reconnect screen the same gesture retries immediately. WebViews without the WebMessage bridge
  keep the gesture disabled rather than risk an accidental reload.
- **Updater discovers releases promptly:** checks now run on launch, on resume and after a page
  load with a 15-minute throttle (5-minute retry after a failure), a 12-hour re-prompt cooldown per
  version, HTTPS-only asset URLs, and a checksum lookup that still resolves when the APK asset was
  renamed.

### Added — Release engineering and repository hygiene

- **`RELEASE.md`** documents the whole release contract: the `release` environment secrets,
  version bump, tag, what CI verifies, post-release checks, key-loss options and rollback.
- **`scripts/release-check.sh`** fails a release when `versionName`, `package.json`,
  `package-lock.json`, the `CHANGELOG` heading and `release_notes.md` disagree.
- **`release.yml` now refuses to publish an APK signed with any key other than the documented
  one** (`EXPECTED_CERT_SHA256`), and gained a `dry_run` input that builds and verifies without
  touching the keystore or publishing. Re-publishing also refreshes the release notes.
- **CodeQL** runs for `java-kotlin` and `javascript-typescript`; `CODEOWNERS`,
  `.editorconfig`, `.nvmrc`, `CITATION.cff` and issue-template contact links were added.
- **`start.sh` / `test.sh`** mirror the Windows helpers (and install dependencies first).
- **`Dockerfile` + `docker-compose.yml`** containerise the optional development proxy.
- **`SECURITY.md` / `CODE_OF_CONDUCT.md`** now point at a real private reporting channel and
  document the enforcement ladder.

### Added — Native Web Capability Bridge (no new app UI)

- **Real download pipeline (`DownloadHandler`):** HTTPS downloads are queued with Android's
  `DownloadManager`, carrying the WebView's Arena session cookies and user agent, with progress in
  the notification shade. `blob:` exports are resolved inside the page and written to Downloads
  through `MediaStore`; `data:` URLs are decoded and saved. Replaces the previous bare
  `ACTION_VIEW` hand-off that could not download authenticated or blob files.
- **Camera capture:** `<input type="file" accept="image/*" capture>` now offers the system camera
  alongside the file picker, with output written through `FileProvider` (new `captures` path).
- **Native JavaScript dialogs:** `alert()`, `confirm()`, `prompt()` and `beforeunload` are handled
  with native dialogs. A stock `WebChromeClient` silently drops these, which broke confirmation and
  "unsaved changes" flows on the site.
- **Fullscreen HTML5 video:** `onShowCustomView` / `onHideCustomView`, with back-button exit.
- **Share sheet bridge:** `navigator.share()` / `navigator.canShare()` are shimmed for Arena origins
  and forwarded to the Android share sheet through an origin-locked `addWebMessageListener` bridge.
- **`intent://` and external schemes:** deep links are parsed with `Intent.parseUri`, honour
  `browser_fallback_url`, and open in the matching system app. `mailto:`, `tel:`, `sms:` and
  `market:` links now work.
- **OAuth popup lifecycle:** secondary windows are tracked, origin-checked, destroyed on dismiss,
  rotation or activity teardown, and no longer leak `Dialog`/`WebView` instances. Ordinary
  `window.open` links return to the main view instead of opening a second full-screen layer.
- **Resilience:** WebView renderer crashes are recovered by recreating the view at the last URL;
  main-frame HTTP 5xx, TLS failures and offline errors render the reconnect screen. Retry now uses
  exponential backoff (5 s → 30 s) and cannot touch a shut-down executor.
- **Accessibility:** WebView text zoom follows the system font scale.

### Security

- **Verified updates (`UpdateVerifier`):** before the installer opens, the APK must match the
  published SHA-256, carry the `ai.arena.mobile` package name, not downgrade the version code, and
  be signed by the same certificate as the installed app. Failed, cancelled or interrupted
  downloads are deleted.
- **Stable release signing:** removed the hard-coded `arena1234` keystore password and the
  `keytool -genkey` step that generated a new key on every CI run. Signing now reads
  `ARENA_RELEASE_*` variables from GitHub Actions secrets; release builds warn loudly and fall back
  to the debug key when unset. `enableV3Signing true` added (v1 + v2 + v3).
- **Correct documentation:** certificate fingerprints, algorithm and validity now match the actual
  v1.0.3 APK; `REQUEST_INSTALL_PACKAGES`, GitHub update checks, third-party cookies and OAuth
  recipients are disclosed in `PRIVACY.md` / `SECURITY.md`.
- **Backup hardening:** Android 12+ `data-extraction-rules.xml` excludes all domains from cloud
  backup and device transfer; `android:enableOnBackInvokedCallback="true"` opts into predictive back.
- **Licensing:** `LICENSE` and `NOTICE` are now bundled inside the APK under `assets/`.

### Changed

- **Updater:** checks are throttled to once per 24 hours (and once per process) instead of firing on
  every page load; drafts and pre-releases are ignored; "Skip This Version" is persisted; release
  notes are shown; downloads show percentage progress, support cancel, check free space and use an
  atomic `.part` → `.apk` rename.
- **CI:** `build.yml` now restores the release keystore from secrets, validates the Gradle wrapper,
  runs lint and `npm audit` (report-only), verifies the APK with `scripts/verify-apk.sh`, caches
  Gradle/npm and declares least-privilege permissions and timeouts. New tag-triggered `release.yml`
  signs, verifies and publishes the APK and `SHA256SUMS` to GitHub Releases.
- **Tooling:** `gradlew` and `scripts/verify-apk.sh` are committed executable; `package.json` is
  `private`; `package-lock.json` name/version corrected; `tests/test-results.json` untracked.
- **Icon script:** now writes to `android/app/src/main/res` (the old path was deleted), adds round
  icons, and `requirements.txt` pins Pillow.
- **Dev server:** `/api/network-info` reports a real LAN address for its QR code instead of
  `127.0.0.1`/`0.0.0.0`, adds `/api/version`, and documents `HOST=0.0.0.0`.

### Tests

- Audit suite expanded from 36 to 92 checks: native capability bridge, update integrity, release
  engineering, the shipped APK artifact (SHA-256, zip alignment, v2 signature block), page-aware
  pull-to-refresh gating, updater discovery/throttling, policy disclosures and development-server
  endpoints.

---

## [1.0.3] - 2026-10-12

### Added & Enhanced

- **Expanded Ecosystem Authentication & Navigation Allow-List:**
  - Added Hugging Face (`huggingface.co`), LMSYS Org (`lmsys.org`, `chat.lmsys.org`), Microsoft (`login.microsoftonline.com`, `live.com`), and Discord (`discord.com`) to in-app allow-list in `MainActivity.java`.
  - Enables smooth SSO login flows and leaderboard navigation without kicking users to external browsers.
- **Automated CI Architecture & Security Test Execution:**
  - Integrated 36-point automated audit compliance suite directly into GitHub Actions CI (`.github/workflows/build.yml`).
- **Dependabot Governance:**
  - Added update groups (`github-actions`, `gradle-dependencies`, `npm-dependencies`) and ignore rules in `.github/dependabot.yml`.
- **Live Upgrade Target:**
  - Published v1.0.3 to GitHub Releases, serving as a live verification update for the in-app self-updater.

---

## [1.0.2] - 2026-10-12

### Fixed & Enhanced

- **Smooth In-App Scrolling & Gestures (Scroll-Up Reload Bug Fixed):**
  - Resolved the critical issue where scrolling up in chat history, battle responses, or leaderboards triggered an unexpected page reload and destroyed conversation state.
  - Disabled `SwipeRefreshLayout`'s touch drag interception (`swipeRefreshLayout.setEnabled(false)` and `swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> true)`).
  - All vertical touch gestures now flow seamlessly and uninterrupted to the underlying WebView and internal DOM containers.

- **In-App Automated Self-Updater from GitHub Releases:**
  - Added background update checker (`AppUpdateManager`) that automatically checks `https://api.github.com/repos/Mr-Nobody-Anonymous/arena-ai-android/releases/latest`.
  - Added semantic version comparison logic to determine if a newer version is published.
  - Prompts the user with a clean, native `AlertDialog` asking if they would like to download and install the update.
  - Downloads the APK directly to app cache and initiates the system package installer via `androidx.core.content.FileProvider`.
  - Configured `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` and `res/xml/file_paths.xml`.
  - Zero persistent UI clutter added to the core application interface.

- **Enhanced Multi-File & Image Upload Bridge:**
  - Refactored `onShowFileChooser` to support complex comma-separated MIME types, extension mapping (e.g. `.png`, `.jpg`), and image wildcards (`image/*`).
  - Added support for `ClipData` multi-selection, allowing multiple benchmark images to be uploaded simultaneously for Vision battles.
  - Guaranteed safe cancellation callbacks so the WebView file picker never hangs.

- **Automated Testing & CI Integration:**
  - Added `AppUpdateManagerTest.java` unit test suite covering semantic version comparisons, tag prefixes, and edge cases.
  - Refactored `NavigationPolicyTest.java` to test production `MainActivity.isHostAllowed(...)` directly.
  - Added Node.js test runner step (`npm test`) directly into GitHub Actions CI workflow (`.github/workflows/build.yml`).
  - Expanded automated audit test suite to 36 out of 36 passing checks (100% pass rate).

- **Ecosystem Navigation & OAuth Enhancements:**
  - Expanded `ALLOWED_HOST_SUFFIXES` in `MainActivity.java` with `huggingface.co`, `lmsys.org`, `login.microsoftonline.com`, `live.com`, and `discord.com` to prevent OAuth session fragmentation.
  - Hardened `DownloadListener` against null or empty download URIs.

- **Dependabot Governance & Branch Hygiene:**
  - Configured update grouping in `.github/dependabot.yml` (`github-actions`, `gradle-dependencies`, `npm-dependencies`).
  - Added ignore rules for incompatible major bumps (`AGP > 8.x`, `gradle-wrapper > 8.x`, `swiperefreshlayout >= 1.2.0`, and merged `kotlin-stdlib-jdk7/8`).
  - Resolved and merged dependency pull requests #11 and #13, declined incompatible PR #12, and pruned remote tracking branches.

---

## [1.0.1] - 2026-10-11

### Fixed & Hardened (Complete Audit Remediation & Mobile Production Release)

- **Android 11–15 Package Installation Resolution ("Invalid Package" Fix):**
  - Resolved installation failure where Android 11–15 devices rejected APKs targeting API 30+ that lacked binary APK Signing Blocks.
  - Enabled both **APK Signature Scheme v1** (JAR signing) and **APK Signature Scheme v2 & v3** via Gradle `signingConfigs.release` and official Android SDK `apksigner`.
  - Replaced ad-hoc jar signing with reproducible community release keystore valid through 2054.
  - Enforced strict 4-byte zip alignment (`zipalign`) on all 241 internal archive entries including `resources.arsc`.

- **5-Second Intelligent Auto-Reconnect Engine:**
  - Implemented automatic network error interception (`onReceivedError`), eliminating raw Chromium error screens (`net::ERR_INTERNET_DISCONNECTED`).
  - Added AMOLED `#000000` reconnect view with Classical Pillar logo, spinning loader, live countdown timer (`Checking in 5s...`), and manual "Retry Now" action.
  - Added background non-blocking connection polling thread pool (`networkExecutor`) probing `https://arena.ai` via `HttpURLConnection` HEAD requests every 5 seconds.
  - Automatic zero-touch recovery reloads `https://arena.ai` the exact moment connectivity is detected.

- **Authentic Adaptive Branding & Material You Support:**
  - Replaced default Android Studio robot vector with the authentic Classical Ionic Pillar vector mark in `@drawable/ic_launcher_foreground.xml`.
  - Applied pure `#000000` AMOLED black background to `@drawable/ic_launcher_background.xml`.
  - Added Android 13+ Material You themed monochrome vector (`@drawable/ic_launcher_monochrome.xml`).
  - Added dual adaptive XML definitions in `mipmap-anydpi-v26/` (`ic_launcher.xml` and `ic_launcher_round.xml`).
  - Pre-rendered 32-bit RGBA density icons across all standard buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) with circular and squircle launcher masks.
  - Configured icon attributes on both `<application>` and `<activity>` tags in `AndroidManifest.xml`.

- **Unified Single-Source Codebase:**
  - Completely retired the legacy decompiled smali tree (`arena_standalone_apk/`, 3,300+ files).
  - Established `android/` (`ai.arena.mobile`) as the single source of truth for all builds and tests.

- **Honest Branding & Legal Compliance:**
  - Dropped all "Official" claims; explicitly designated the project as an **Unofficial Community Client**.
  - Added clear disclaimers of non-affiliation with Arena, arena.ai, and LMSYS Org.
  - Added `LICENSE` (Apache-2.0) and `NOTICE` with third-party and trademark acknowledgments.

- **Client-Side Privacy Controls:**
  - Documented authentic direct HTTPS connection architecture to `https://arena.ai`.
  - Disabled HTML5 Geolocation API (`WebSettings.setGeolocationEnabled(false)`).
  - Restricted local file scheme access (`setAllowFileAccess(false)` and `setAllowContentAccess(false)`).
  - Sanitized User-Agent to clean Android Chrome standard (stripped `; wv` wrapper marker).
  - Enforced `android:usesCleartextTraffic="false"` and `android:allowBackup="false"`.
  - Kept only minimal required permission (`android.permission.INTERNET`).

- **Navigation Security Allow-List:**
  - Restricted embedded WebView navigation to Arena (`arena.ai`, `lmarena.ai`) and authorized identity providers (`accounts.google.com`, `github.com`, `appleid.apple.com`, `auth0.com`).
  - All outside URLs automatically hand off to the system web browser via `Intent.ACTION_VIEW`.

- **Modern Platform UX:**
  - Migrated from deprecated `onKeyDown`/`onBackPressed` to AndroidX `OnBackPressedCallback` for full Android 14+ / 16 (API 34–36) compatibility.
  - Upgraded `WebChromeClient.onShowFileChooser` to support multi-file uploads (`ClipData`), MIME type parsing, and safe cancellation for Vision battles.
  - Implemented `WebChromeClient.onCreateWindow` popup dialogs for OAuth workflows.
  - Added `SwipeRefreshLayout` with dark-mode styling for pull-to-refresh.
  - Ensured session cookie persistence via `CookieManager.getInstance().flush()` during activity lifecycle events.

- **Build Infrastructure & Automation:**
  - Added `android/gradle.properties` (`android.useAndroidX=true`), Gradle 8.2 wrapper, and ProGuard / R8 rules.
  - Added automated GitHub Actions CI workflow (`.github/workflows/build.yml`) for linting, testing, and release signing.
  - Configured Dependabot and consolidated dependencies.
  - Added 19 search-optimized GitHub repository topics.
  - Established 35/35 passing automated audit test suite.

---

## [1.0.0] - 2026-10-10

### Added
- Initial standalone APK release connecting to `https://arena.ai`.
- Direct pairwise model comparisons, leaderboard views, and persistent cookie sessions.
