# Privacy Policy

**Effective Date:** October 11, 2026  
**Application:** Arena.ai Unofficial Android Client (`ai.arena.mobile`)

This privacy policy explains how data is handled by the Arena.ai mobile client application.

---

## 1. Zero Telemetry & Zero Data Collection

The Arena.ai Android application itself **does not collect, log, track, or transmit any personal data, analytics, crash logs, or telemetry** to any third-party or developer servers.
- No analytics SDKs (e.g., Firebase, Google Analytics, Mixpanel, Adjust, Sentry) are bundled.
- No background trackers, ad networks, or device fingerprinting libraries exist in the application.
- There are no custom middleman servers or proxy relays required for normal mobile usage.

---

## 2. Direct Network Communication & Connectivity Monitoring

The application functions as a dedicated, standalone browser interface connecting directly over encrypted HTTPS to **[https://arena.ai](https://arena.ai)**.
- All network requests, API queries, model battle prompts, votes, and chat interactions travel directly between your Android device and Arena's official infrastructure.
- As with any standard web browser visiting `https://arena.ai`, Arena and its content delivery network (Cloudflare) will see your device's public IP address, TLS handshake, and standard browser headers.
- **Offline Reconnection Probing:** When the device loses internet connectivity, the application's auto-reconnect engine periodically issues minimal HTTP HEAD requests directly to `https://arena.ai` on a background thread solely to test connectivity before restoring the page. No tracking parameters, identifiers, or personal tokens are included in these health checks.

### 2.1 Update Checks and Downloads (GitHub)

The self-updater is the only non-Arena network traffic the app initiates:

- At most once every 15 minutes (and at most once per app session), the app requests `https://api.github.com/repos/Mr-Nobody-Anonymous/arena-ai-android/releases/latest`; a failed check is retried after 5 minutes. GitHub receives your public IP address, an `ArenaAI-Mobile-Client` user agent and the request timestamp. No Arena cookies, account details or device identifiers are sent.
- If a newer release exists, the app may also fetch the release's `SHA256SUMS` asset to obtain the expected checksum.
- The APK is downloaded only after you tap **Update Now**. It is downloaded directly from GitHub (or its release-asset CDN), written to the app's private storage, verified against the published SHA-256 and the installed app's signing certificate, and then handed to Android's package installer. If Android still needs the *install unknown apps* permission, the verified file and its checksum are remembered locally (and re-verified) so the install can resume when you come back; it is removed once that version is installed.
- **Skip This Version** is stored locally and suppresses that prompt until a different version is published.
- Files you download from Arena itself are written to the public Downloads collection through Android's `DownloadManager`/`MediaStore` and are not uploaded anywhere.

---

## 3. Client-Side Privacy Controls

The application enforces strict client-side privacy safeguards inside the WebView engine:

| Control | Implementation | Purpose |
|---|---|---|
| **Geolocation Disabled** | `WebSettings.setGeolocationEnabled(false)` | Prevents websites and scripts from requesting physical GPS / coordinate location via the HTML5 Geolocation API. |
| **Local File Access Blocked** | `WebSettings.setAllowFileAccess(false)`, `setAllowContentAccess(false)` | Blocks JavaScript from accessing private local files on your device via `file://` or content providers. |
| **Clean Chrome User-Agent** | Standard Android Chrome User-Agent string | Eliminates custom application wrapper signatures from HTTP request headers. |
| **Navigation Allow-List** | Strict host matching in `WebViewClient` | Any link navigating outside Arena (`arena.ai`, `lmarena.ai`) or supported OAuth identity providers is automatically handed off to the system web browser. |
| **Isolated Local Storage** | Standard Android private sandbox | Session cookies and local storage tokens are held in private app storage (`/data/data/ai.arena.mobile/`) with `android:allowBackup="false"` to prevent automated adb data extraction. |
| **Third-Party Cookies** | `CookieManager.setAcceptThirdPartyCookies(..., true)` | Required by OAuth/SSO windows (Google, GitHub, Apple, Microsoft, Discord, Hugging Face). They live in the app's private cookie jar and are never inspected by native code. |
| **Download Isolation** | `DownloadManager` / `MediaStore` | Downloaded files are placed in Downloads; the app never reads unrelated files from your device. |

---

## 4. Permissions Requested

The application declares exactly two permissions:

- `android.permission.INTERNET` — required to load `https://arena.ai` and to check GitHub for updates.
- `android.permission.REQUEST_INSTALL_PACKAGES` — used only to open Android's package installer for a verified self-update. Installing still requires an explicit **Update Now** tap and the OS-level *Install unknown apps* approval for Arena AI.

AndroidX also contributes an app-private, signature-level `ai.arena.mobile.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` at build time; it is not a user-granted permission.

The app **does not request** or use:
- Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)
- Camera (`CAMERA` — photos are taken by your camera app, which returns a single image)
- Audio recording (`RECORD_AUDIO`)
- Storage reading/writing (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`)
- Phone state (`READ_PHONE_STATE`)
- Network state (`ACCESS_NETWORK_STATE`)

## 4.1 Third-Party Recipients

| Recipient | Data | When |
| :--- | :--- | :--- |
| Arena (arena.ai) and its CDN | Page requests, prompts, votes, cookies, IP address | While using the app |
| GitHub (api.github.com, release-asset CDN) | IP address, user agent, update-check timestamp, APK download | At most once every 15 minutes (once per session); on update |
| OAuth providers you choose (Google, GitHub, Apple, Microsoft, Discord, Hugging Face) | Standard OAuth/SSO traffic | Only when you start that sign-in flow |

No analytics, advertising, crash-reporting or telemetry service receives any data.

---

## 5. Contact & Questions

If you have questions about this privacy statement or wish to inspect the implementation, the complete source code is publicly accessible and auditable on GitHub:  
[https://github.com/Mr-Nobody-Anonymous/arena-ai-android](https://github.com/Mr-Nobody-Anonymous/arena-ai-android)
