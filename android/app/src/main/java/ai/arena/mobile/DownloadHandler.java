package ai.arena.mobile;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Turns WebView download requests into something the user can actually keep.
 *
 * The Arena web app produces three kinds of downloads:
 * <ul>
 *   <li>ordinary HTTPS links (model weights, exports, attachments) — queued with the
 *       platform {@link DownloadManager} so the notification shade shows progress and
 *       the request carries the user's Arena session cookies;</li>
 *   <li>{@code blob:} URLs created by client-side JavaScript (common for "Download"
 *       buttons on generated text/images) — resolved through the page's own Blob store
 *       and written to Downloads;</li>
 *   <li>{@code data:} URLs — decoded and written to Downloads.</li>
 * </ul>
 *
 * DownloadManager, MediaStore and the notification shade are system surfaces, so this
 * adds no app-specific UI on top of the Arena web interface.
 */
final class DownloadHandler {

    /**
     * Hard ceiling for a {@code blob:} export, measured on the blob itself inside the page.
     * Anything larger is refused before a base64 copy (which is ~33 % bigger than the
     * payload) is built in the renderer.
     */
    private static final long MAX_BLOB_BYTES = 48L * 1024L * 1024L;
    private static final int BLOB_POLL_INTERVAL_MS = 250;
    private static final int BLOB_POLL_ATTEMPTS = 120; // 30 seconds

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Owner of the single in-page blob handshake. Keyed by WebView rather than a plain
     * boolean, and paired with a deadline, so a poll whose callback never arrives (the page
     * or WebView was destroyed underneath it) cannot block every later download until the
     * process restarts.
     *
     * <p>Held weakly on purpose: a WebView pins its activity, so a strong static here would
     * trade one leak for a worse one. Identity is compared through the referent, and a cleared
     * reference is treated as "unowned" so the lock can always be taken over.
     */
    @Nullable
    private static volatile WeakReference<WebView> blobLockOwner = null;
    private static volatile long blobLockDeadline = 0L;

    /**
     * The one pending retry of the poll loop. Held so it can be removed from the main handler
     * when its WebView goes away: each {@code postDelayed} lambda captures the activity and the
     * source WebView, so an unremoved chain kept a destroyed Activity (and a WebView whose
     * renderer was gone) alive for the remaining 30 seconds — and kept calling
     * {@code evaluateJavascript} on it.
     */
    @Nullable
    private static volatile Runnable pendingBlobPoll = null;

    private DownloadHandler() {
    }

    static void handle(Activity activity, WebView source, String url, String userAgent,
                       String contentDisposition, String mimeType, long contentLength) {
        if (activity == null || url == null) {
            return;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String lower = trimmed.toLowerCase(Locale.US);

        if (lower.startsWith("blob:")) {
            downloadBlob(activity, source, trimmed, mimeType, contentDisposition);
            return;
        }
        if (lower.startsWith("data:")) {
            downloadDataUri(activity, trimmed, mimeType, contentDisposition);
            return;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            openWithSystem(activity, Uri.parse(trimmed));
            return;
        }
        enqueueSystemDownload(activity, trimmed, userAgent, contentDisposition, mimeType);
    }

    // ---------------------------------------------------------------------------------
    // HTTPS downloads
    // ---------------------------------------------------------------------------------

    private static void enqueueSystemDownload(Activity activity, String url, String userAgent,
                                              String contentDisposition, String mimeType) {
        String fileName = sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType));
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(activity, R.string.download_no_manager, Toast.LENGTH_SHORT).show();
            openWithSystem(activity, Uri.parse(url));
            return;
        }

        try {
            DownloadManager.Request request = buildRequest(activity, url, userAgent, mimeType, fileName, true);
            manager.enqueue(request);
            Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception publicDirFailure) {
            try {
                DownloadManager.Request fallback = buildRequest(activity, url, userAgent, mimeType, fileName, false);
                manager.enqueue(fallback);
                Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                openWithSystem(activity, Uri.parse(url));
            }
        }
    }

    private static DownloadManager.Request buildRequest(Activity activity, String url, String userAgent,
                                                        String mimeType, String fileName, boolean publicDir) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

        String cookies = CookieManager.getInstance().getCookie(url);
        if (!TextUtils.isEmpty(cookies)) {
            request.addRequestHeader("Cookie", cookies);
        }
        String ua = userAgent;
        if (TextUtils.isEmpty(ua)) {
            ua = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Mobile Safari/537.36";
        }
        request.addRequestHeader("User-Agent", ua);

        if (!TextUtils.isEmpty(mimeType)) {
            request.setMimeType(mimeType);
        }
        request.setTitle(fileName);
        request.setDescription(activity.getString(R.string.download_notification_description));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);

        if (publicDir) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        } else {
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
        }
        return request;
    }

    // ---------------------------------------------------------------------------------
    // data: URLs
    // ---------------------------------------------------------------------------------

    private static void downloadDataUri(Activity activity, String url, String mimeType,
                                        String contentDisposition) {
        try {
            if (url.length() > MAX_BLOB_BYTES / 3 * 4 + 1024) {
                // Decoding would allocate another ~75 % of the string on top of it.
                Toast.makeText(activity, R.string.download_too_large, Toast.LENGTH_SHORT).show();
                return;
            }
            int comma = url.indexOf(',');
            if (comma < 0) {
                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String header = url.substring(5, comma);
            String payload = url.substring(comma + 1);
            boolean isBase64 = header.toLowerCase(Locale.US).contains(";base64");
            String declaredMime = header.split(";")[0];

            String effectiveMime = !TextUtils.isEmpty(declaredMime)
                    ? declaredMime
                    : (!TextUtils.isEmpty(mimeType) ? mimeType : "application/octet-stream");

            byte[] bytes = isBase64
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);

            String fileName = sanitizeFileName(
                    URLUtil.guessFileName("arena-download", contentDisposition, effectiveMime));
            if (!fileName.contains(".")) {
                fileName = fileName + "." + extensionForMime(effectiveMime);
            }
            saveBytes(activity, bytes, effectiveMime, fileName);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------------------------
    // blob: URLs
    // ---------------------------------------------------------------------------------

    private static void downloadBlob(Activity activity, WebView source, String blobUrl,
                                      String mimeType, String contentDisposition) {
        if (source == null) {
            return;
        }
        // The in-page handshake uses one set of global variables, so only one export can be
        // in flight per page. The lock is owned by the WebView and carries a deadline, so a
        // destroyed source or a callback that never fires can no longer wedge every later
        // download for the rest of the process (see tryAcquireBlobLock).
        if (!tryAcquireBlobLock(source)) {
            Toast.makeText(activity, R.string.download_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        // The byte cap is enforced inside the page, before FileReader materialises the
        // base64 form: checking only on the Java side would run after the whole payload has
        // already been built in the renderer and copied across JNI as a String, which is
        // precisely the OOM the limit is meant to prevent.
        String safeUrl = toJavaScriptStringLiteral(blobUrl);
        String script =
                "window.__arenaBlobState='PENDING';window.__arenaBlobData=null;window.__arenaBlobMime='';"
                        + "(function(){try{"
                        + "fetch(" + safeUrl + ").then(function(r){return r.blob();}).then(function(b){"
                        + "if(!b){window.__arenaBlobState='ERROR';return;}"
                        + "if(b.size>" + MAX_BLOB_BYTES + "){window.__arenaBlobState='TOO_LARGE';return;}"
                        + "var fr=new FileReader();"
                        + "fr.onload=function(){window.__arenaBlobState='OK';window.__arenaBlobData=fr.result;"
                        + "window.__arenaBlobMime=b.type||'';};"
                        + "fr.onerror=function(){window.__arenaBlobState='ERROR';};"
                        + "fr.readAsDataURL(b);"
                        + "}).catch(function(){window.__arenaBlobState='ERROR';});"
                        + "}catch(e){window.__arenaBlobState='ERROR';}})();";

        try {
            source.evaluateJavascript(script, null);
        } catch (Exception e) {
            releaseBlobLock(source);
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        pollBlob(activity, source, mimeType, contentDisposition, 0);
    }

    /**
     * Encodes a value as a double-quoted JavaScript string literal, escaping every character
     * that could terminate the literal or the surrounding statement. Hand-rolling
     * {@code \} and {@code '} only was fragile: a raw newline in the URL produced a script
     * that failed to compile, silently cancelling the export.
     */
    private static String toJavaScriptStringLiteral(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c < 0x20 || c == 0x2028 || c == 0x2029 || c > 0x7e) {
                out.append("\\u").append(String.format(Locale.US, "%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Claims the single in-page blob handshake for {@code owner}. A stale claim (its deadline
     * passed, or its WebView was destroyed) is taken over instead of blocking forever.
     */
    private static synchronized boolean tryAcquireBlobLock(WebView owner) {
        long now = System.currentTimeMillis();
        // Only a provably dead claim may be taken over: either its WebView is gone (weak
        // referent cleared) or the deadline elapsed. A live owner always gets the "already in
        // progress" answer, because the in-page handshake has a single set of globals and a
        // second concurrent export would overwrite them and mix the two files up.
        WebView current = blobLockOwner == null ? null : blobLockOwner.get();
        if (current != null && now < blobLockDeadline) {
            return false;
        }
        blobLockOwner = new WeakReference<>(owner);
        blobLockDeadline = now + (long) BLOB_POLL_ATTEMPTS * BLOB_POLL_INTERVAL_MS + 5000L;
        return true;
    }

    /** Releases the claim, but never one belonging to a different (still living) WebView. */
    private static synchronized void releaseBlobLock(@Nullable WebView owner) {
        WebView current = blobLockOwner == null ? null : blobLockOwner.get();
        if (owner == null || blobLockOwner == null || current == owner) {
            blobLockOwner = null;
            blobLockDeadline = 0L;
            cancelPendingBlobPoll();
        }
    }

    /**
     * Drops the queued retry. Each poll lambda captures the activity and the source WebView,
     * so a chain left in the main handler kept a destroyed Activity — and a WebView whose
     * renderer was already gone — reachable for up to 30 seconds, calling
     * {@code evaluateJavascript} on it while it did.
     */
    private static synchronized void cancelPendingBlobPoll() {
        Runnable pending = pendingBlobPoll;
        pendingBlobPoll = null;
        if (pending != null) {
            MAIN_HANDLER.removeCallbacks(pending);
        }
    }

    /**
     * Called when a WebView is destroyed (activity teardown or renderer-crash recovery) so an
     * interrupted poll can neither leave every later download reporting "already in progress"
     * nor retain the screen that owns it.
     */
    public static void onWebViewDestroyed(WebView view) {
        releaseBlobLock(view);
    }

    private static void pollBlob(Activity activity, WebView source, String mimeType,
                                 String contentDisposition, int attempt) {
        pendingBlobPoll = null;
        if (attempt >= BLOB_POLL_ATTEMPTS || activity.isFinishing() || activity.isDestroyed()) {
            releaseBlobLock(source);
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try {
            source.evaluateJavascript("window.__arenaBlobState||'PENDING'", value -> {
                String state = asString(value);
                if ("OK".equals(state)) {
                    readBlobPayload(activity, source, mimeType, contentDisposition);
                } else if ("TOO_LARGE".equals(state)) {
                    releaseBlobLock(source);
                    Toast.makeText(activity, R.string.download_too_large, Toast.LENGTH_SHORT).show();
                } else if ("ERROR".equals(state)) {
                    releaseBlobLock(source);
                    Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                } else {
                    // Named and remembered, so onWebViewDestroyed() can remove it.
                    Runnable next = () ->
                            pollBlob(activity, source, mimeType, contentDisposition, attempt + 1);
                    pendingBlobPoll = next;
                    MAIN_HANDLER.postDelayed(next, BLOB_POLL_INTERVAL_MS);
                }
            });
        } catch (Exception e) {
            releaseBlobLock(source);
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static void readBlobPayload(Activity activity, WebView source, String mimeType,
                                        String contentDisposition) {
        try {
            source.evaluateJavascript(
                    "window.__arenaBlobData+'\\u0000'+window.__arenaBlobMime",
                    value -> {
                        releaseBlobLock(source);
                        try {
                            String combined = asString(value);
                            if (combined == null || combined.isEmpty()) {
                                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            int separator = combined.lastIndexOf('\u0000');
                            String dataUrl = separator >= 0 ? combined.substring(0, separator) : combined;
                            String blobMime = separator >= 0 ? combined.substring(separator + 1) : "";

                            // Second line of defence behind the in-page b.size check: the
                            // page could have been navigated between the two evaluations, or
                            // an older shim could have skipped the size guard. base64 grows
                            // by 4/3, so this is the same ceiling in characters.
                            if (dataUrl.length() > MAX_BLOB_BYTES / 3 * 4 + 1024) {
                                Toast.makeText(activity, R.string.download_too_large, Toast.LENGTH_SHORT).show();
                                return;
                            }

                            int comma = dataUrl.indexOf(',');
                            if (comma < 0) {
                                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String header = dataUrl.substring(0, comma);
                            String payload = dataUrl.substring(comma + 1);
                            boolean isBase64 = header.toLowerCase(Locale.US).contains(";base64");

                            String declaredMime = header.startsWith("data:")
                                    ? header.substring(5).split(";")[0]
                                    : "";
                            String effectiveMime = !TextUtils.isEmpty(blobMime)
                                    ? blobMime
                                    : (!TextUtils.isEmpty(declaredMime)
                                    ? declaredMime
                                    : (!TextUtils.isEmpty(mimeType) ? mimeType : "application/octet-stream"));

                            byte[] bytes = isBase64
                                    ? Base64.decode(payload, Base64.DEFAULT)
                                    : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);

                            String fileName = sanitizeFileName(
                                    URLUtil.guessFileName("arena-download", contentDisposition, effectiveMime));
                            if (!fileName.contains(".")) {
                                fileName = fileName + "." + extensionForMime(effectiveMime);
                            }
                            saveBytes(activity, bytes, effectiveMime, fileName);
                        } catch (Exception e) {
                            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                        } finally {
                            try {
                                source.evaluateJavascript(
                                        "window.__arenaBlobData=null;window.__arenaBlobState='';window.__arenaBlobMime='';",
                                        null);
                            } catch (Exception ignored) {
                            }
                        }
                    });
        } catch (Exception e) {
            releaseBlobLock(source);
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static String asString(String javascriptResult) {
        if (javascriptResult == null || "null".equals(javascriptResult)) {
            return null;
        }
        try {
            Object parsed = new JSONTokener(javascriptResult).nextValue();
            if (parsed instanceof String) {
                return (String) parsed;
            }
            if (parsed instanceof JSONObject) {
                return parsed.toString();
            }
            return String.valueOf(parsed);
        } catch (Exception e) {
            return javascriptResult;
        }
    }

    // ---------------------------------------------------------------------------------
    // Writing bytes
    // ---------------------------------------------------------------------------------

    private static void saveBytes(Activity activity, byte[] data, String mimeType, String fileName) {
        if (data == null || data.length == 0) {
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri item = activity.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (item == null) {
                    Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                try (OutputStream out = activity.getContentResolver().openOutputStream(item)) {
                    if (out == null) {
                        throw new IllegalStateException("No output stream");
                    }
                    out.write(data);
                    out.flush();
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                activity.getContentResolver().update(item, values, null, null);
                Toast.makeText(activity, R.string.download_saved, Toast.LENGTH_SHORT).show();
                return;
            }

            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                dir = activity.getFilesDir();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            File target = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(target)) {
                out.write(data);
                out.flush();
            }
            try {
                MediaScannerConnection.scanFile(activity,
                        new String[]{target.getAbsolutePath()}, new String[]{mimeType}, null);
            } catch (Exception ignored) {
            }
            Toast.makeText(activity, R.string.download_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static String extensionForMime(String mimeType) {
        if (mimeType == null) {
            return "bin";
        }
        String extension = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType);
        return TextUtils.isEmpty(extension) ? "bin" : extension;
    }

    private static String sanitizeFileName(String candidate) {
        String name = candidate == null ? "" : candidate.trim();
        if (name.isEmpty()) {
            name = "arena-download";
        }
        // Strip directory separators and characters that are illegal on Android storage.
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            name = "arena-download";
        }
        if (name.length() > 120) {
            String extension = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0 && name.length() - dot <= 12) {
                extension = name.substring(dot);
            }
            name = name.substring(0, 120 - extension.length()) + extension;
        }
        return name;
    }

    private static void openWithSystem(Activity activity, Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.external_link_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
