package ai.arena.mobile;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Verifies a downloaded update before Android's package installer ever sees it.
 *
 * Three independent properties are checked:
 * <ol>
 *   <li><b>Integrity</b> — the file matches the SHA-256 published by the release
 *       (either the {@code SHA256SUMS} asset or GitHub's asset digest);</li>
 *   <li><b>Identity</b> — the archive's package name is this application and the
 *       version code is not a downgrade;</li>
 *   <li><b>Provenance</b> — the archive is signed by exactly the same certificate(s)
 *       as the installed app, which is also what Android enforces at install time.</li>
 * </ol>
 *
 * Without these checks a corrupted, truncated, or swapped download would only be
 * caught by the installer (or not at all), which is exactly the failure mode the
 * previous updater had.
 */
final class UpdateVerifier {

    enum Reason {
        OK,
        MISSING_HASH,
        HASH_MISMATCH,
        PARSE_ERROR,
        PACKAGE_MISMATCH,
        VERSION_DOWNGRADE,
        SIGNER_MISMATCH
    }

    static final class Result {
        final Reason reason;
        final String detail;

        Result(Reason reason, String detail) {
            this.reason = reason;
            this.detail = detail;
        }

        Result(Reason reason) {
            this(reason, null);
        }

        boolean ok() {
            return reason == Reason.OK;
        }
    }

    private UpdateVerifier() {
    }

    static Result verify(Context context, File apkFile, String expectedSha256) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) {
            return new Result(Reason.PARSE_ERROR, "empty file");
        }
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            return new Result(Reason.MISSING_HASH);
        }

        String actualSha256 = sha256(apkFile);
        if (actualSha256 == null) {
            return new Result(Reason.HASH_MISMATCH, "unreadable file");
        }
        if (!actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
            return new Result(Reason.HASH_MISMATCH, "sha256 " + actualSha256);
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo archive = getArchiveInfo(packageManager, apkFile, true);
        if (archive == null) {
            return new Result(Reason.PARSE_ERROR, "not an APK");
        }

        if (!context.getPackageName().equals(archive.packageName)) {
            return new Result(Reason.PACKAGE_MISMATCH, archive.packageName);
        }

        long archiveVersionCode = versionCode(archive);
        long installedVersionCode = versionCode(getInstalledInfo(context));
        if (archiveVersionCode < installedVersionCode) {
            return new Result(Reason.VERSION_DOWNGRADE,
                    archiveVersionCode + " < " + installedVersionCode);
        }

        List<String> archiveCertificates = certificateDigests(archive);
        if (archiveCertificates.isEmpty()) {
            // getPackageArchiveInfo() populates SigningInfo inconsistently: on several OEM
            // builds it comes back empty for an archive even though the JAR signature is
            // present, which would refuse *every* update on those devices. Retry with the
            // pre-P flag before concluding anything about the signer, so a platform quirk
            // cannot be mistaken for a mismatch.
            PackageInfo legacy = getArchiveInfo(packageManager, apkFile, false);
            archiveCertificates = certificateDigests(legacy);
        }
        List<String> installedCertificates = certificateDigests(getInstalledInfo(context));
        if (archiveCertificates.isEmpty() || installedCertificates.isEmpty()) {
            return new Result(Reason.SIGNER_MISMATCH, "missing certificates");
        }
        if (!archiveCertificates.equals(installedCertificates)) {
            return new Result(Reason.SIGNER_MISMATCH, "certificate mismatch");
        }

        return new Result(Reason.OK);
    }

    static String sha256(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scans the downloaded archive.
     *
     * @param preferSigningCertificates {@code true} to ask for the API 28+
     *                                    {@code SigningInfo}, {@code false} to fall back to the
     *                                    legacy {@code signatures} array.
     */
    @SuppressWarnings("deprecation")
    private static PackageInfo getArchiveInfo(PackageManager packageManager, File apkFile,
                                              boolean preferSigningCertificates) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferSigningCertificates) {
                return packageManager.getPackageArchiveInfo(
                        apkFile.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            }
            return packageManager.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo getInstalledInfo(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return context.getPackageManager().getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            }
            return context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (Exception e) {
            return null;
        }
    }

    private static long versionCode(PackageInfo info) {
        if (info == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static List<String> certificateDigests(PackageInfo info) {
        if (info == null) {
            return Collections.emptyList();
        }
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            SigningInfo signingInfo = info.signingInfo;
            signatures = signingInfo.hasMultipleSigners()
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        }
        if (signatures == null || signatures.length == 0) {
            // Pre-P path, and the archive case where SigningInfo was handed back empty.
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return Collections.emptyList();
        }

        List<String> digests = new ArrayList<>(signatures.length);
        for (Signature signature : signatures) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digests.add(toHex(digest.digest(signature.toByteArray())));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(digests);
        return digests;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
