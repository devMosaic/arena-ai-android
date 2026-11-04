package ai.arena.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Arena.ai Unofficial Mobile Client
 *
 * A deliberately chrome-free, full-screen WebView shell around Arena (arena.ai).
 *
 * The activity intentionally adds no navigation bars, banners, floating buttons, or
 * menus of its own: every screen the user sees is rendered by the Arena web app.
 * Native code only fills capability gaps that a WebView cannot provide on its own
 * (file uploads, camera capture, downloads, JavaScript dialogs, fullscreen media,
 * OAuth popups, share sheet handoff, session persistence, renderer recovery, and
 * the in-app self-updater).
 */
public class MainActivity extends AppCompatActivity {

    public static final String ARENA_HOME_URL = "https://arena.ai";

    public static final String OFFLINE_HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\"><title>Arena.ai</title><style>*{box-sizing:border-box;margin:0;padding:0}body{background:#000;color:#fff;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px;text-align:center;overflow:hidden}.logo{width:76px;height:76px;margin-bottom:24px;filter:drop-shadow(0 0 16px rgba(255,255,255,.15))}h1{font-size:20px;font-weight:600;margin-bottom:8px}p{font-size:14px;color:#888;line-height:1.5;margin-bottom:24px;max-width:280px}.spinner{width:32px;height:32px;border:3px solid rgba(255,255,255,.15);border-top-color:#fff;border-radius:50%;animation:spin .8s linear infinite;margin-bottom:20px}@keyframes spin{to{transform:rotate(360deg)}}.status{font-size:13px;color:#38bdf8;font-weight:500;margin-bottom:24px}.btn{background:#fff;color:#000;border:none;padding:12px 28px;border-radius:20px;font-size:14px;font-weight:600;cursor:pointer;box-shadow:0 4px 14px rgba(255,255,255,.15)}</style></head><body><svg class=\"logo\" viewBox=\"0 -21.5 167 167\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\"><path fill=\"#fff\" d=\"M57.5364 53.3142C52.1508 53.3142 47.7887 57.6763 47.7887 63.0618V123.88H53.9963V63.0618C53.9963 61.0989 55.5902 59.505 57.5531 59.505C59.5161 59.505 61.11 61.0989 61.11 63.0618V123.88H67.3176V63.0618C67.3176 57.6763 62.9555 53.3142 57.5699 53.3142H57.5364Z\"/><path fill=\"#fff\" d=\"M109.121 53.3142C103.735 53.3142 99.3734 57.6763 99.3734 63.0618V123.88H105.581V63.0618C105.581 61.0989 107.175 59.505 109.138 59.505C111.101 59.505 112.695 61.0989 112.695 63.0618V123.88H118.902V63.0618C118.902 57.6763 114.54 53.3142 109.155 53.3142H109.121Z\"/><path fill=\"#fff\" d=\"M83.3286 53.3142C77.9431 53.3142 73.5809 57.6763 73.5809 63.0618V123.88H79.7886V63.0618C79.7886 61.0989 81.3824 59.505 83.3454 59.505C85.3083 59.505 86.9022 61.0989 86.9022 63.0618V123.88H93.1098V63.0618C93.1098 57.6763 88.7477 53.3142 83.3622 53.3142H83.3286Z\"/><path fill=\"#fff\" d=\"M143.782 28.035L148.077 20.5691H158.06L166.667 0H0L8.60681 20.5691H18.5894L22.8676 28.035C14.5292 30.8872 7.66727 38.135 7.66727 49.0571C7.66727 59.9792 15.5526 69.2235 27.3639 69.2235C29.9477 69.2235 32.3468 68.6867 34.4776 67.7471V123.901H40.6684V62.7978C42.6146 60.1637 43.7555 56.8754 43.8393 53.1508C43.8393 53.1005 43.8561 53.0333 43.8561 52.9662C43.8561 52.9159 43.8393 52.8823 43.8393 52.832C43.8393 52.7985 43.8561 52.7649 43.8561 52.7314C43.8561 52.681 43.8393 52.6307 43.8393 52.5636C43.7722 50.047 43.1011 47.6981 41.9267 45.7016H124.74C123.566 47.6981 122.894 50.047 122.827 52.5636C122.827 52.6307 122.811 52.681 122.811 52.7314V52.9662C122.811 52.9662 122.827 53.1005 122.827 53.1508C122.911 56.8754 124.052 60.1637 125.998 62.7978V123.901H132.189V67.7471C134.32 68.6867 136.702 69.2235 139.303 69.2235C151.097 69.2235 158.999 59.5765 158.999 49.0571C158.999 38.5377 152.121 30.8872 143.782 28.035ZM12.734 14.3615L9.31146 6.20764H157.355L153.933 14.3615H12.734ZM140.93 20.5691L137.491 26.5586H29.1759L25.7365 20.5691H140.93ZM139.303 63.0159C133.246 63.0159 129.085 58.9222 129.018 52.8991C129.052 48.6377 131.803 45.7855 135.796 45.7352H135.914C138.414 45.7352 140.46 47.7988 140.46 49.9631V50.0637C140.41 51.3221 139.403 52.2951 138.145 52.2951C136.434 52.2951 135.041 53.6877 135.041 55.399C135.041 57.1103 136.434 58.5028 138.145 58.5028C142.843 58.5028 146.685 54.6943 146.685 49.9631C146.685 49.8121 146.651 49.6611 146.634 49.5269C146.383 43.9736 141.836 39.5443 135.83 39.5443H83.3166L82.9139 39.5275H82.4945L30.8369 39.5443C24.8305 39.5443 20.2839 43.9736 20.0322 49.5269C20.0154 49.6611 19.9819 49.8121 19.9819 49.9631C19.9819 54.6943 23.8239 58.5028 28.5216 58.5028C30.2329 58.5028 31.6254 57.1103 31.6254 55.399C31.6254 53.6877 30.2329 52.2951 28.5216 52.2951C27.2633 52.2951 26.2566 51.3221 26.1895 50.0637C26.1895 50.0302 26.2063 49.9966 26.2063 49.9631C26.2063 47.7988 28.2364 45.7352 30.753 45.7352H30.8536C34.8634 45.7855 37.6149 48.6377 37.6485 52.8991C37.5814 58.9222 33.4038 63.0159 27.3639 63.0159C19.4114 63.0159 13.8749 56.5902 13.8749 49.0571C13.8749 38.89 21.8442 32.7495 31.6925 32.7495H134.974C144.822 32.7495 152.792 38.89 152.792 49.0571C152.792 56.5902 147.255 63.0159 139.303 63.0159Z\"/></svg><h1>Connecting to Arena</h1><p>No internet connection detected.<br>Retrying automatically...</p><div class=\"spinner\"></div><div class=\"status\" id=\"cd\">Checking in 5s...</div><button class=\"btn\" onclick=\"retry()\">Retry Now</button><script>var c=5,t=null;function tick(){c--;if(c<=0){c=5}document.getElementById(\"cd\").innerText=\"Checking in \"+c+\"s...\";t=setTimeout(tick,1000)}function retry(){if(t)clearTimeout(t);document.getElementById(\"cd\").innerText=\"Reconnecting...\";window.location.href=\"https://arena.ai\"}t=setTimeout(tick,1000)</script></body></html>";

    /**
     * Every host that may be rendered inside the app (Arena properties, model hubs and
     * OAuth identity providers). Anything else is handed to the system browser.
     */
    public static final List<String> ALLOWED_HOST_SUFFIXES =
            Collections.unmodifiableList(Arrays.asList(
                    "arena.ai",
                    "lmarena.ai",
                    "lmsys.org",
                    "huggingface.co",
                    "accounts.google.com",
                    "github.com",
                    "appleid.apple.com",
                    "auth0.com",
                    "login.microsoftonline.com",
                    "login.live.com",
                    "discord.com"
            ));

    /** Hosts that genuinely need to stay inside a secondary window (OAuth/SSO). */
    private static final List<String> AUTH_HOST_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
            "accounts.google.com",
            "github.com",
            "appleid.apple.com",
            "auth0.com",
            "login.microsoftonline.com",
            "login.live.com",
            "discord.com",
            "huggingface.co"
    ));

    /** Arena-owned hosts, where the native JavaScript shims are injected. */
    private static final List<String> INTERNAL_HOST_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
            "arena.ai",
            "lmarena.ai",
            "lmsys.org"
    ));

    /**
     * Document URL used for the local reconnect page. It is deliberately distinct from
     * the real Arena URL so {@code onPageFinished} can tell the offline screen apart
     * from a successful load of the site.
     */
    private static final String OFFLINE_BASE_URL = "https://arena.ai/__offline__";

    private static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider";
    private static final int INITIAL_RETRY_DELAY_MS = 5000;
    private static final int MAX_RETRY_DELAY_MS = 30000;

    /**
     * Installs {@code navigator.share}/{@code navigator.canShare} for the Arena web app so
     * its share buttons open the Android share sheet. The AndroidX WebMessage bridge is
     * origin-locked to Arena hosts, so no other site can reach the native handler.
     */
    private static final String SHARE_SHIM_JS =
            "(function(){try{"
                    + "if(window.__arenaNativeShim){return;}window.__arenaNativeShim=true;"
                    + "var bridge=window.arenaAndroid;"
                    + "if(!bridge||typeof bridge.postMessage!=='function'){return;}"
                    + "function send(payload){try{bridge.postMessage(JSON.stringify(payload));}catch(e){}}"
                    + "if(typeof navigator.share!=='function'){"
                    + "navigator.share=function(data){data=data||{};"
                    + "send({type:'share',title:data.title||'',text:data.text||'',url:data.url||''});"
                    + "return Promise.resolve();};"
                    + "navigator.canShare=function(){return true;};"
                    + "}"
                    + "}catch(e){}})();";

    /**
     * Reports whether the page can still be pulled down, i.e. the document is at the top
     * <em>and</em> no visible nested scroll container is scrolled. Arena renders chats and
     * battles inside {@code overflow-y: scroll} containers while the WebView's own root stays
     * at 0, so a native-only check cannot tell "at the top" from "scrolled deep in history".
     * Scroll events (including nested ones, captured on window) update the state and only
     * transitions are posted over the origin-locked bridge.
     */
    private static final String VIEWPORT_SHIM_JS =
            "(function(){try{"
                    + "if(window.__arenaViewportShim){return;}window.__arenaViewportShim=true;"
                    + "var bridge=window.arenaAndroid;"
                    + "if(!bridge||typeof bridge.postMessage!=='function'){return;}"
                    + "var last=null,scrolled=[],pending=false,primed=false;"
                    + "function root(){var d=document.scrollingElement||document.documentElement;"
                    + "return (window.pageYOffset||(d&&d.scrollTop)||0)<=0;}"
                    + "function track(el){if(!el||el.nodeType!==1){return;}var i=scrolled.indexOf(el);"
                    + "if(el.scrollTop>1){if(i<0){scrolled.push(el);}}else if(i>=0){scrolled.splice(i,1);}}"
                    + "function prime(){if(primed){return;}primed=true;"
                    + "try{var all=document.querySelectorAll('*');"
                    + "for(var i=0;i<all.length;i++){if(all[i].scrollTop>1){scrolled.push(all[i]);}}}catch(e){}}"
                    + "function compute(){if(!root()){return false;}"
                    + "for(var i=scrolled.length-1;i>=0;i--){var el=scrolled[i];"
                    + "if(!el.isConnected||el.scrollTop<=1){scrolled.splice(i,1);continue;}"
                    + "var r=el.getBoundingClientRect();"
                    + "if(r.bottom>0&&r.top<window.innerHeight&&r.right>0&&r.left<window.innerWidth){return false;}}"
                    + "return true;}"
                    + "function report(){pending=false;prime();var atTop=compute();if(atTop===last){return;}"
                    + "last=atTop;"
                    + "try{bridge.postMessage(JSON.stringify({type:'viewport',atTop:atTop}));}catch(e){}}"
                    + "function schedule(){if(pending){return;}pending=true;"
                    + "(window.requestAnimationFrame||function(cb){setTimeout(cb,16);})(report);}"
                    + "window.addEventListener('scroll',function(e){"
                    + "track(e.target===document?(document.scrollingElement||document.documentElement):e.target);"
                    + "schedule();},true);"
                    + "window.addEventListener('resize',schedule);"
                    + "window.addEventListener('load',schedule);"
                    + "setTimeout(schedule,0);setTimeout(schedule,400);setTimeout(schedule,1500);"
                    + "}catch(e){}})();";

    private boolean isOfflineState = false;
    private boolean destroyed = false;
    private int retryDelayMs = INITIAL_RETRY_DELAY_MS;

    /**
     * True while the web app reports that neither the document nor any visible nested
     * scroller is scrolled away from its top. Pull-to-refresh is armed only in that
     * state; this is what keeps "scroll up through a battle" from reloading the page.
     *
     * It starts false and is only set by the page itself (see {@link #VIEWPORT_SHIM_JS}),
     * so old WebViews without the WebMessage bridge simply keep the gesture disabled
     * instead of bringing the accidental-reload bug back.
     */
    private volatile boolean pageAtTop = false;
    private volatile boolean pageLoading = true;
    private volatile boolean viewportTrackingAvailable = false;

    /**
     * Whether the document currently on screen is an Arena origin, i.e. whether the
     * viewport shim was injected there at all. On any other allow-listed host
     * (an OAuth provider, a docs page) no shim runs, so {@link #pageAtTop} would never
     * be updated and the refresh gesture would be permanently dead; those pages scroll
     * the root document and are handled with the native {@code getScrollY()} check
     * instead (see {@link #canPullToRefresh()}).
     */
    private volatile boolean currentPageInternal = false;

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout rootLayout;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    /** Last insets applied to {@link #rootLayout}, so fullscreen can restore them exactly. */
    @Nullable
    private Insets lastSystemBarInsets;

    /** Camera capture target created for {@code <input type="file" capture>} requests. */
    private Uri pendingCameraUri;
    private File pendingCameraFile;

    /** Scratch directory for {@code <input capture>} stills; pruned at every launch. */
    private static final String CAMERA_CAPTURE_DIR = "captures";

    /** Secondary OAuth / SSO windows, tracked so they can always be torn down. */
    private final Map<Dialog, WebView> popupWindows = new HashMap<>();

    /** Fullscreen HTML5 video surface. */
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    /**
     * Last system-bar/cutout insets handed to the shell. {@code exitFullscreen()} restores
     * them directly because {@code requestApplyInsets()} is only a request: if nothing has
     * changed, the listener never runs again and the page stays under the status bar.
     */
    @Nullable
    private Insets lastSystemBarInsets;

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isOfflineState || destroyed) {
                return;
            }
            if (networkExecutor.isShutdown()) {
                return;
            }
            // Reachable-for-the-duration-of-the-probe only: the socket can block for 5 s and
            // HttpURLConnection does not respond to the interrupt from shutdownNow(), so a
            // strong capture here would keep the destroyed activity (and its WebView) alive for
            // the whole probe.
            final WeakReference<MainActivity> self = new WeakReference<>(this);
            networkExecutor.execute(() -> {
                boolean reachable = false;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(ARENA_HOME_URL).openConnection();
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setRequestMethod("HEAD");
                    reachable = conn.getResponseCode() > 0;
                } catch (Exception ignored) {
                    reachable = false;
                } finally {
                    if (conn != null) {
                        try {
                            conn.disconnect();
                        } catch (Exception ignored) {
                        }
                    }
                }

                final boolean isReachable = reachable;
                runOnUiThread(() -> {
                    MainActivity host = self.get();
                    if (host == null || host.destroyed || !host.isOfflineState) {
                        return;
                    }
                    if (isReachable) {
                        host.retryDelayMs = INITIAL_RETRY_DELAY_MS;
                        host.isOfflineState = false;
                        if (host.webView != null) {
                            host.webView.loadUrl(ARENA_HOME_URL);
                        }
                    } else {
                        host.retryDelayMs = Math.min(host.retryDelayMs * 2, MAX_RETRY_DELAY_MS);
                        host.scheduleNetworkRetry();
                    }
                });
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (WebView.getCurrentWebViewPackage() == null) {
            Toast.makeText(this, R.string.webview_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Modern back navigation (predictive-back compatible on API 33+).
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenView != null) {
                    exitFullscreen();
                    return;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> deliverFileChooserResult(result.getResultCode(), result.getData())
        );

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(Color.WHITE);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.BLACK);
        swipeRefreshLayout.setDistanceToTriggerSync(
                Math.round(64 * getResources().getDisplayMetrics().density));

        // Pull-to-refresh is armed only when the page itself says it is at the very top
        // (see VIEWPORT_SHIM_JS). The WebView's root scroll position is always 0 for the
        // SPA, so a native-only check would turn "scroll up in a chat" into a reload.
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> !canPullToRefresh());

        webView = createWebView();

        swipeRefreshLayout.addView(
                webView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        );
        rootLayout.addView(
                swipeRefreshLayout,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        );

        setContentView(rootLayout);
        applySystemBarInsets();

        // Any leftover camera stills belong to a previous session; the WebView has
        // already read (or abandoned) them, so they must not accumulate in the cache.
        pruneStaleCameraCaptures();

        configureWebView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            if (webView.getUrl() == null) {
                webView.loadUrl(ARENA_HOME_URL);
            }
        } else {
            webView.loadUrl(ARENA_HOME_URL);
        }
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        return view;
    }

    private int systemFontZoom() {
        try {
            float scale = getResources().getConfiguration().fontScale;
            return Math.max(80, Math.min(200, Math.round(scale * 100f)));
        } catch (Exception ignored) {
            return 100;
        }
    }

    /**
     * WebView settings that must be identical for the main window and every popup.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void applyHardenedSettings(WebView view) {
        WebSettings settings = view.getSettings();

        // Core web platform capabilities required by the Arena single-page app.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(systemFontZoom());

        // Popups are required for OAuth/SSO flows.
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // Client-side privacy controls.
        settings.setGeolocationEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);

        // Never auto-darken the Arena UI; the web app owns its own theme.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
            } catch (Exception ignored) {
            }
        }

        // Clean Chrome User-Agent (avoids the WebView wrapper marker).
        String currentUA = settings.getUserAgentString();
        if (currentUA != null && currentUA.contains("; wv")) {
            settings.setUserAgentString(currentUA.replace("; wv", ""));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(final WebView view) {
        applyHardenedSettings(view);

        // Session & cookie persistence.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);

        // Pulling down at the top reloads the current page; on the reconnect screen the
        // same gesture retries the connection immediately.
        swipeRefreshLayout.setOnRefreshListener(() -> {
            retryHandler.removeCallbacks(retryRunnable);
            if (isOfflineState) {
                isOfflineState = false;
                retryDelayMs = INITIAL_RETRY_DELAY_MS;
                view.loadUrl(ARENA_HOME_URL);
                return;
            }
            String current = view.getUrl();
            if (current == null || current.startsWith(OFFLINE_BASE_URL)) {
                view.loadUrl(ARENA_HOME_URL);
            } else {
                view.reload();
            }
        });

        installShareBridge(view);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView wv, WebResourceRequest request) {
                return handleNavigation(request == null ? null : request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView wv, String url) {
                return url != null && handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView wv, String url, Bitmap favicon) {
                super.onPageStarted(wv, url, favicon);
                pageLoading = true;
                // Until the freshly loaded page reports its own scroll position, pulling
                // down must not reload anything.
                pageAtTop = false;
                currentPageInternal = url != null && isInternalHost(Uri.parse(url).getHost());
            }

            @Override
            public void onPageFinished(WebView wv, String url) {
                super.onPageFinished(wv, url);
                swipeRefreshLayout.setRefreshing(false);
                pageLoading = false;
                if (url != null && url.startsWith(OFFLINE_BASE_URL)) {
                    // The reconnect screen finished loading: keep the retry loop alive.
                    pageAtTop = true;
                    CookieManager.getInstance().flush();
                    return;
                }
                if (url != null && url.startsWith("https://")) {
                    onPageSuccess();
                    injectPageShims(wv, url);
                    // Throttled internally; also runs from onResume.
                    AppUpdateManager.checkForUpdates(MainActivity.this);
                }
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView wv, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(wv, request, error);
                if (request != null && request.isForMainFrame()) {
                    swipeRefreshLayout.setRefreshing(false);
                    pageLoading = false;
                    pageAtTop = false;
                    handleNetworkError();
                }
            }


            @Override
            public void onReceivedHttpError(WebView wv, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(wv, request, errorResponse);
                if (request != null && request.isForMainFrame() && errorResponse != null
                        && errorResponse.getStatusCode() >= 500) {
                    pageLoading = false;
                    pageAtTop = false;
                    handleNetworkError();
                }
            }

            @Override
            public void onReceivedSslError(WebView wv, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this, R.string.ssl_error, Toast.LENGTH_LONG).show();
            }

            @Override
            public boolean onRenderProcessGone(WebView wv, RenderProcessGoneDetail detail) {
                return recoverFromRendererCrash(wv);
            }
        });

        view.setWebChromeClient(createChromeClient(false, null));

        view.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                DownloadHandler.handle(MainActivity.this, view, url, userAgent,
                        contentDisposition, mimeType, contentLength);
            }
        });
    }

    /**
     * Handles in-app vs external navigation, including non-HTTP schemes.
     */
    private boolean handleNavigation(@Nullable Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String lowerScheme = scheme.toLowerCase(Locale.US);

        if (lowerScheme.equals("http") || lowerScheme.equals("https")) {
            if (isHostAllowed(uri.getHost())) {
                return false; // Render inside the WebView.
            }
            openExternal(uri);
            return true;
        }

        // Pseudo-schemes that must stay inside the current document.
        if (lowerScheme.equals("about") || lowerScheme.equals("data")
                || lowerScheme.equals("blob") || lowerScheme.equals("javascript")
                || lowerScheme.equals("chrome")) {
            return false;
        }

        openExternal(uri);
        return true;
    }

    /**
     * Opens a URI in the corresponding system app (mail, dialer, maps, Play Store,
     * {@code intent://} deep links, ...). Falls back to the link's
     * {@code browser_fallback_url} when nothing can handle it.
     *
     * <p>{@code intent://} URIs are parsed with {@link Intent#URI_INTENT_SCHEME}, which
     * lets the page name an explicit {@code component=} target. Launching that verbatim is
     * the classic WebView intent-redirection primitive: page content could aim an
     * attacker-chosen action at any exported activity on the device, and categories are not
     * matched for explicit intents, so adding {@code CATEGORY_BROWSABLE} alone would not
     * stop it. The component, selector and package are therefore stripped (see
     * {@link #toBrowsableIntent}), leaving an implicit browsable intent that only genuine
     * browser or deep-link filters can resolve.
     */
    private void openExternal(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        boolean intentScheme = "intent".equalsIgnoreCase(uri.getScheme());
        Intent intent = intentScheme ? toBrowsableIntent(uri) : new Intent(Intent.ACTION_VIEW, uri);
        if (intent != null) {
            try {
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
                // Fall through to the browser_fallback_url below.
            }
        }

        Uri fallback = intentScheme ? readBrowserFallbackUrl(uri) : null;
        if (fallback != null) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, fallback));
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, R.string.external_link_unavailable, Toast.LENGTH_SHORT).show();
    }

    /**
     * Turns an {@code intent://} URI into an <em>implicit</em> intent: whatever component,
     * selector or package the page tried to pin is removed before it can be launched.
     *
     * @return the intent to launch, or {@code null} if the URI could not be parsed.
     */
    @Nullable
    private Intent toBrowsableIntent(Uri uri) {
        try {
            Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            parsed.setComponent(null);
            parsed.setSelector(null);
            parsed.setPackage(null);
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extracts {@code browser_fallback_url} from an {@code intent://} URI, accepting only a
     * plain http(s) target so a failed deep link can never smuggle in another intent, a
     * {@code file://} path or a content provider URI.
     */
    @Nullable
    private Uri readBrowserFallbackUrl(Uri uri) {
        try {
            Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            String fallback = parsed.getStringExtra("browser_fallback_url");
            if (fallback == null || fallback.trim().isEmpty()) {
                return null;
            }
            Uri candidate = Uri.parse(fallback.trim());
            String scheme = candidate.getScheme();
            if (scheme == null) {
                return null;
            }
            String lower = scheme.toLowerCase(Locale.US);
            if (!lower.equals("https") && !lower.equals("http")) {
                return null;
            }
            return candidate;
        } catch (Exception ignored) {
            return null;
        }
    }

    private WebChromeClient createChromeClient(final boolean isPopup, @Nullable final Runnable onCloseWindow) {
        return new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100 && swipeRefreshLayout != null && !isPopup) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                    MainActivity.this.filePathCallback = null;
                }
                MainActivity.this.filePathCallback = callback;

                Intent intent = buildFileChooserIntent(params);
                try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, getString(R.string.file_chooser_title)));
                    return true;
                } catch (ActivityNotFoundException e) {
                    cancelFileChooser();
                    Toast.makeText(MainActivity.this, R.string.no_file_manager, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (isPopup || resultMsg == null || resultMsg.obj == null) {
                    return false;
                }
                openPopupWindow(resultMsg);
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                if (onCloseWindow != null) {
                    onCloseWindow.run();
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                showAlert(message, result);
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                showConfirm(message, result);
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                showPrompt(message, defaultValue, result);
                return true;
            }

            @Override
            public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                showBeforeUnload(message, result);
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        };
    }

    // ---------------------------------------------------------------------------------
    // JavaScript dialogs (native, transient UI only)
    // ---------------------------------------------------------------------------------

    private void showAlert(String message, JsResult result) {
        if (isFinishing()) {
            result.cancel();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> result.confirm())
                .setOnCancelListener(dialog -> result.cancel())
                .setCancelable(true)
                .show();
    }

    private void showConfirm(String message, JsResult result) {
        if (isFinishing()) {
            result.cancel();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> result.confirm())
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> result.cancel())
                .setOnCancelListener(dialog -> result.cancel())
                .setCancelable(true)
                .show();
    }

    private void showPrompt(String message, String defaultValue, JsPromptResult result) {
        if (isFinishing()) {
            result.cancel();
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        if (defaultValue != null) {
            input.setText(defaultValue);
            input.setSelection(input.getText().length());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> result.confirm(input.getText().toString()))
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> result.cancel())
                .setOnCancelListener(dialog -> result.cancel())
                .setCancelable(true)
                .show();
    }

    private void showBeforeUnload(String message, JsResult result) {
        if (isFinishing()) {
            result.confirm();
            return;
        }
        String text = (message == null || message.trim().isEmpty())
                ? getString(R.string.leave_page_message)
                : message;
        new AlertDialog.Builder(this)
                .setTitle(R.string.leave_page_title)
                .setMessage(text)
                .setPositiveButton(R.string.leave_page_confirm, (dialog, which) -> result.confirm())
                .setNegativeButton(R.string.leave_page_cancel, (dialog, which) -> result.cancel())
                .setOnCancelListener(dialog -> result.cancel())
                .setCancelable(true)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // Fullscreen HTML5 video
    // ---------------------------------------------------------------------------------

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setVisibility(View.GONE);
        }
        if (rootLayout != null) {
            // A fullscreen video uses the entire surface, insets included.
            rootLayout.setPadding(0, 0, 0, 0);
            rootLayout.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void exitFullscreen() {
        if (fullscreenView == null) {
            return;
        }
        if (rootLayout != null) {
            rootLayout.removeView(fullscreenView);
        }
        fullscreenView = null;
        if (rootLayout != null) {
            // Re-apply the cached insets directly: requestApplyInsets() is only a request,
            // and if the inset values have not changed the listener may never run again,
            // which would leave the page permanently under the status bar after a video.
            Insets bars = lastSystemBarInsets;
            if (bars != null) {
                rootLayout.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            ViewCompat.requestApplyInsets(rootLayout);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setVisibility(View.VISIBLE);
        }
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) {
            callback.onCustomViewHidden();
        }
    }

    // ---------------------------------------------------------------------------------
    // Secondary windows (OAuth / SSO)
    // ---------------------------------------------------------------------------------

    private void openPopupWindow(Message resultMsg) {
        final WebView popup = createWebView();
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        popupWindows.put(dialog, popup);

        applyHardenedSettings(popup);
        CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);

        popup.setWebChromeClient(createChromeClient(true, () -> closePopup(dialog)));
        popup.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                DownloadHandler.handle(this, popup, url, userAgent, contentDisposition, mimeType, contentLength));

        popup.setWebViewClient(new WebViewClient() {
            private boolean firstNavigation = true;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handlePopupNavigation(dialog, request == null ? null : request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url != null && handlePopupNavigation(dialog, Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!firstNavigation || url == null) {
                    return;
                }
                firstNavigation = false;
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                // A normal "open in new window" link should not become a second UI layer:
                // hand it to the main WebView. Only genuine auth flows keep the popup.
                if (host != null && isInternalHost(host) && !isAuthHost(host)) {
                    if (webView != null) {
                        webView.loadUrl(url);
                    }
                    closePopup(dialog);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                closePopup(dialog);
                return true;
            }
        });

        dialog.setContentView(popup);
        dialog.setOnDismissListener(d -> closePopup(dialog));
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                closePopup(dialog);
                return true;
            }
            return false;
        });
        dialog.show();

        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(popup);
        resultMsg.sendToTarget();
    }

    private boolean handlePopupNavigation(Dialog dialog, @Nullable Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        String scheme = uri.getScheme();
        if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && isHostAllowed(host)) {
            return false;
        }
        if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")
                && (scheme.equalsIgnoreCase("about") || scheme.equalsIgnoreCase("data")
                || scheme.equalsIgnoreCase("blob") || scheme.equalsIgnoreCase("javascript"))) {
            return false;
        }
        closePopup(dialog);
        openExternal(uri);
        return true;
    }

    private void closePopup(Dialog dialog) {
        if (dialog == null) {
            return;
        }
        WebView popup = popupWindows.remove(dialog);
        try {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception ignored) {
        }
        if (popup != null) {
            try {
                popup.stopLoading();
                DownloadHandler.onWebViewDestroyed(popup);
                popup.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Share bridge
    // ---------------------------------------------------------------------------------

    private void installShareBridge(WebView view) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return;
        }
        // Must cover every host in INTERNAL_HOST_SUFFIXES: the shims are injected on those
        // pages, and a page whose origin is not registered here gets no bridge object, so
        // both shims bail out and pull-to-refresh silently dies on that host.
        Set<String> origins = new HashSet<>(Arrays.asList(
                "https://arena.ai",
                "https://*.arena.ai",
                "https://lmarena.ai",
                "https://*.lmarena.ai",
                "https://lmsys.org",
                "https://*.lmsys.org"
        ));
        try {
            WebViewCompat.addWebMessageListener(view, "arenaAndroid", origins,
                    (WebView webView, WebMessageCompat message, Uri sourceOrigin,
                     boolean isMainFrame, JavaScriptReplyProxy replyProxy) -> {
                        if (message == null || message.getData() == null) {
                            return;
                        }
                        try {
                            JSONObject payload = new JSONObject(message.getData());
                            String type = payload.optString("type");
                            if ("share".equals(type)) {
                                share(payload.optString("title"), payload.optString("text"),
                                        payload.optString("url"));
                            } else if ("viewport".equals(type)) {
                                pageAtTop = payload.optBoolean("atTop", false);
                            }
                        } catch (Exception ignored) {
                        }
                    });
            viewportTrackingAvailable = true;
        } catch (Exception ignored) {
        }
    }

    private void injectPageShims(WebView view, String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        currentPageInternal = host != null && isInternalHost(host);
        if (!currentPageInternal) {
            return;
        }
        try {
            view.evaluateJavascript(SHARE_SHIM_JS, null);
            view.evaluateJavascript(VIEWPORT_SHIM_JS, null);
        } catch (Exception ignored) {
        }
    }

    private void share(@Nullable String title, @Nullable String text, @Nullable String url) {
        try {
            StringBuilder body = new StringBuilder();
            if (text != null) {
                body.append(text);
            }
            if (url != null && !url.isEmpty()) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(url);
            }
            if (body.length() == 0 && title != null) {
                body.append(title);
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, body.toString());
            if (title != null && !title.isEmpty()) {
                send.putExtra(Intent.EXTRA_SUBJECT, title);
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser_title)));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------------------------
    // File uploads + camera capture
    // ---------------------------------------------------------------------------------

    private Intent buildFileChooserIntent(@Nullable WebChromeClient.FileChooserParams fileChooserParams) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] acceptTypes = fileChooserParams != null ? fileChooserParams.getAcceptTypes() : null;
        List<String> validMimeTypes = new ArrayList<>();

        if (acceptTypes != null) {
            for (String rawType : acceptTypes) {
                if (rawType != null && !rawType.trim().isEmpty()) {
                    String[] parts = rawType.split(",");
                    for (String part : parts) {
                        String clean = part.trim();
                        if (!clean.isEmpty()) {
                            if (clean.startsWith(".")) {
                                String ext = clean.substring(1).toLowerCase(Locale.US);
                                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                                if (mime != null) {
                                    validMimeTypes.add(mime);
                                }
                            } else {
                                validMimeTypes.add(clean);
                            }
                        }
                    }
                }
            }
        }

        if (validMimeTypes.isEmpty()) {
            intent.setType("*/*");
        } else if (validMimeTypes.size() == 1) {
            intent.setType(validMimeTypes.get(0));
        } else {
            boolean allImages = true;
            for (String mime : validMimeTypes) {
                if (!mime.startsWith("image/")) {
                    allImages = false;
                    break;
                }
            }
            intent.setType(allImages ? "image/*" : "*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, validMimeTypes.toArray(new String[0]));
        }

        if (fileChooserParams != null && fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        // Vision Arena and voice notes benefit from shooting a photo directly.
        if (fileChooserParams != null && fileChooserParams.isCaptureEnabled()) {
            Intent camera = createCameraIntent();
            if (camera != null) {
                intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{camera});
            }
        }

        return intent;
    }

    private Intent createCameraIntent() {
        try {
            File dir = new File(getCacheDir(), CAMERA_CAPTURE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File photo = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            Uri outputUri = FileProvider.getUriForFile(
                    this, getPackageName() + FILE_PROVIDER_AUTHORITY_SUFFIX, photo);

            Intent capture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            capture.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, outputUri);
            capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // On Android 11+ this requires the <queries> entry declared in the manifest.
            if (getPackageManager().queryIntentActivities(capture, 0).isEmpty()) {
                return null;
            }

            pendingCameraUri = outputUri;
            pendingCameraFile = photo;
            return capture;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void deliverFileChooserResult(int resultCode, @Nullable Intent data) {
        // Claim the pending still first. The previous version nulled the fields and only then
        // asked the cleanup helper to delete, which re-read those (now null) fields and so
        // never deleted anything; the delete now works on the captured locals.
        Uri cameraUri = pendingCameraUri;
        File cameraFile = pendingCameraFile;
        pendingCameraUri = null;
        pendingCameraFile = null;

        if (filePathCallback == null) {
            deleteCameraCapture(cameraUri, cameraFile);
            return;
        }

        Uri[] results = null;
        boolean ok = resultCode == Activity.RESULT_OK;

        if (ok && cameraUri != null && cameraFile != null && cameraFile.exists() && cameraFile.length() > 0) {
            // Keep the file: the renderer reads the content:// URI lazily, after this
            // callback returns. pruneStaleCameraCaptures() reclaims it at the next launch.
            results = new Uri[]{cameraUri};
        } else {
            deleteCameraCapture(cameraUri, cameraFile);
            if (ok && data != null) {
                ClipData clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        results[i] = clipData.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (data.getDataString() != null) {
                    try {
                        results = new Uri[]{Uri.parse(data.getDataString())};
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        try {
            filePathCallback.onReceiveValue(results);
        } finally {
            filePathCallback = null;
        }
    }

    private void deleteCameraCapture(@Nullable Uri uri, @Nullable File file) {
        if (file != null) {
            try {
                // Removing the file also drops the FileProvider grant with it, so there is
                // no need to go through the ContentResolver as well.
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception ignored) {
            }
            return;
        }
        if (uri != null) {
            try {
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Drops a capture that was never delivered (cancelled chooser, destroyed activity).
     */
    private void discardPendingCameraCapture() {
        Uri uri = pendingCameraUri;
        File file = pendingCameraFile;
        pendingCameraUri = null;
        pendingCameraFile = null;
        deleteCameraCapture(uri, file);
    }

    /**
     * Removes camera stills left behind by earlier sessions. A capture handed to the
     * WebView has to stay on disk until the page has read it, so it cannot be deleted when
     * the chooser returns; pruning at launch is what bounds {@code cache/captures/}.
     */
    private void pruneStaleCameraCaptures() {
        try {
            File[] stale = new File(getCacheDir(), CAMERA_CAPTURE_DIR).listFiles();
            if (stale == null) {
                return;
            }
            for (File file : stale) {
                try {
                    file.delete();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void cancelFileChooser() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        discardPendingCameraCapture();
    }

    // ---------------------------------------------------------------------------------
    // Connectivity
    // ---------------------------------------------------------------------------------

    /**
     * Android 15 (targetSdk 35) draws every app edge-to-edge and ignores the legacy
     * status/navigation bar colours. Padding the shell with the system-bar and cutout
     * insets keeps the web page inside exactly the same usable rectangle it had before,
     * with the bars still painted black by the theme.
     */
    private void applySystemBarInsets() {
        if (rootLayout == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            lastSystemBarInsets = bars;
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(rootLayout);
    }

    /**
     * Whether the pull-down gesture may start a refresh right now.
     *
     * <p>The reconnect screen always allows it (the gesture means "retry now"). An Arena
     * page must have finished loading and reported that the document <em>and</em> every
     * visible nested scroller sit at the top. Any other allow-listed page is checked
     * natively instead, and {@code getScrollY() <= 0} is a cheap belt-and-braces guard in
     * both cases.
     */
    private boolean canPullToRefresh() {
        WebView view = webView;
        if (view == null || destroyed) {
            return false;
        }
        if (isOfflineState) {
            return true;
        }
        if (pageLoading) {
            return false;
        }
        if (!currentPageInternal || !viewportTrackingAvailable) {
            // No shim runs on a foreign (OAuth / docs) page and older WebView engines
            // cannot host the bridge at all, so nothing can report the scroll state of
            // nested scrollers. Those pages scroll the root document, which is exactly
            // what getScrollY() observes; the reload bug lived in Arena's nested
            // scrollers, where the native check is unreliable and the shim is used.
            return view.getScrollY() <= 0;
        }
        return pageAtTop && view.getScrollY() <= 0;
    }

    private void handleNetworkError() {
        if (destroyed) {
            return;
        }
        isOfflineState = true;
        if (webView != null) {
            webView.loadDataWithBaseURL(OFFLINE_BASE_URL, OFFLINE_HTML, "text/html", "UTF-8", null);
        }
        scheduleNetworkRetry();
    }

    private void scheduleNetworkRetry() {
        if (!isOfflineState || destroyed) {
            return;
        }
        retryHandler.removeCallbacks(retryRunnable);
        retryHandler.postDelayed(retryRunnable, retryDelayMs);
    }

    private void onPageSuccess() {
        isOfflineState = false;
        retryDelayMs = INITIAL_RETRY_DELAY_MS;
        retryHandler.removeCallbacks(retryRunnable);
    }

    // ---------------------------------------------------------------------------------
    // Host policy
    // ---------------------------------------------------------------------------------

    public static boolean isHostAllowed(@Nullable String host) {
        return matchesHost(host, ALLOWED_HOST_SUFFIXES);
    }

    static boolean isInternalHost(@Nullable String host) {
        return matchesHost(host, INTERNAL_HOST_SUFFIXES);
    }

    static boolean isAuthHost(@Nullable String host) {
        return matchesHost(host, AUTH_HOST_SUFFIXES);
    }

    private static boolean matchesHost(@Nullable String host, List<String> allowed) {
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.US);
        for (String suffix : allowed) {
            if (lowerHost.equals(suffix) || lowerHost.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------
    // Renderer recovery
    // ---------------------------------------------------------------------------------

    private boolean recoverFromRendererCrash(WebView crashedView) {
        if (destroyed) {
            return true;
        }
        String lastUrl = null;
        try {
            lastUrl = crashedView.getUrl();
        } catch (Exception ignored) {
        }

        try {
            ViewGroup parent = (ViewGroup) crashedView.getParent();
            if (parent != null) {
                parent.removeView(crashedView);
            }
            // The renderer took any in-flight blob export down with it; release the
            // DownloadHandler claim so the next download is not blocked forever.
            DownloadHandler.onWebViewDestroyed(crashedView);
            crashedView.destroy();
        } catch (Exception ignored) {
        }

        if (swipeRefreshLayout == null) {
            return true;
        }

        webView = createWebView();
        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView(webView);

        Toast.makeText(this, R.string.renderer_restarted, Toast.LENGTH_SHORT).show();
        webView.loadUrl(lastUrl != null ? lastUrl : ARENA_HOME_URL);
        return true;
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------

    /**
     * {@code fontScale} and {@code density} are listed in {@code android:configChanges}, so
     * the activity is deliberately not recreated when the user changes the system font size
     * (recreating would throw away the running page). That also means nothing would pick the
     * new scale up, so textZoom is recomputed here.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WebView view = webView;
        if (view != null) {
            view.getSettings().setTextZoom(systemFontZoom());
        }
        if (rootLayout != null) {
            ViewCompat.requestApplyInsets(rootLayout);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        CookieManager.getInstance().flush();
        AppUpdateManager.checkPendingInstall(this);
        // Throttled inside; this makes a release that appeared while the app was in the
        // background show up as soon as the user comes back instead of after a navigation.
        AppUpdateManager.checkForUpdates(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        retryHandler.removeCallbacks(retryRunnable);

        // The updater keeps its progress dialog in a static field; it must not outlive us.
        AppUpdateManager.onActivityDestroyed(this);

        exitFullscreen();
        cancelFileChooser();

        for (Dialog dialog : new ArrayList<>(popupWindows.keySet())) {
            closePopup(dialog);
        }
        popupWindows.clear();

        if (webView != null) {
            try {
                if (webView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) webView.getParent()).removeView(webView);
                }
                webView.stopLoading();
                DownloadHandler.onWebViewDestroyed(webView);
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }

        if (!networkExecutor.isShutdown()) {
            networkExecutor.shutdownNow();
        }

        super.onDestroy();
    }
}
