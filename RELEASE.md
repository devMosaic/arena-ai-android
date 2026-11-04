# Release Process

This repository ships **signed APKs through GitHub Releases**, and the Android client
updates itself from that same feed. A release is therefore not just an upload: the
artifact must be signed with the **same key as the previous release**, or every installed
copy will refuse the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` from Android,
`SIGNER_MISMATCH` from `UpdateVerifier`).

Everything below is automated by `.github/workflows/release.yml`; this document is the
contract and the checklist.

---

## 1. Prerequisites (one-time, repository settings)

Create an environment named **`release`** (Settings → Environments) and add four secrets
to it. Use a required-reviewer rule if more than one person can push tags.

| Secret | Value |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` of the **original** signing key |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | key password |

The workflow **fails** if any of these is missing — it will never publish a debug-signed
APK. Nothing is ever generated in CI: a new key would break every existing install.

> The certificate of the released key is documented in `README.md` and `SECURITY.md`
> (`CN=Arena Mobile Community`, SHA-256 `0A:78:95:E6:…:F2:DE`). If a rebuilt binary shows
> a different fingerprint, **stop** — you are about to publish an incompatible update.

---

## 2. Prepare the version

1. Bump `versionCode` and `versionName` in `android/app/build.gradle`.
   `versionCode` must always increase; `versionName` must equal the tag without the `v`.
2. Keep `package.json`, `package-lock.json` and the top `CHANGELOG.md` heading in sync
   (`npm version --no-git-tag-version` does most of this).
3. Update `release_notes.md` — it becomes the GitHub release body **and** the text shown
   inside the in-app update dialog, so keep it readable and free of HTML.
4. Run the pre-flight check:

   ```bash
   ./scripts/release-check.sh
   ```

   It fails if any version drifts, and prints the exact tag command on success.

---

## 3. Verify locally (optional but recommended)

```bash
npm ci
npm test                      # 123-check audit suite
(cd android && ./gradlew test assembleRelease)
scripts/verify-apk.sh android/app/build/outputs/apk/release/app-release.apk
```

Without the keystore the release APK is debug-signed; `verify-apk.sh` still checks
signature presence, 4-byte alignment and package metadata.

The audit suite does not stop at the build files: it opens `ArenaAI.apk` itself, recomputes every
v1 (JAR) digest, walks the APK Signing Block for v2/v3 pairs, extracts the X.509 from
`META-INF/CERT.RSA` and compares its SHA-256 with the fingerprint published here *and* the
`EXPECTED_CERT_SHA256` pin in `release.yml`, reads the `versionName` out of the binary manifest,
and fails if the committed artifact is neither current with the source nor explicitly disclosed as
a historical artifact.

> **Until a `v1.0.4` tag exists, `releases/latest` serves 1.0.3 to everyone** — both the README
> install link and the in-app updater. It is also why the integrity hardening added in 1.0.4
> (archive signature verification, blob-export limits, persisted deferred installs) is not yet
> reachable by anyone on 1.0.3: publishing is the fix, not another source change.

---

## 4. Tag and publish

```bash
git tag v1.0.4
git push origin v1.0.4
```

`release.yml` then runs, in order:

1. checkout → JDK 17 → Android SDK → wrapper validation
2. restore the keystore from the `release` secrets (hard-fails when absent)
3. check the tag matches `versionName`
4. `npm ci` + `npm test`
5. `./gradlew test assembleRelease`
6. `scripts/verify-apk.sh` — `apksigner --print-certs`, `zipalign -c -v 4`, `aapt badging`
7. `sha256sum ArenaAI.apk > SHA256SUMS`
8. create the release with `release_notes.md` as the body, attaching **`ArenaAI.apk`** and
   **`SHA256SUMS`** (if the release already exists, the assets are replaced)

A tag that does not match `versionName` aborts before anything is published.

---

## 5. After publishing

- [ ] The release page lists `ArenaAI.apk` **and** `SHA256SUMS`
- [ ] `cat SHA256SUMS` matches the APK downloaded from the release page
- [ ] `apksigner verify --print-certs ArenaAI.apk` prints the expected certificate
- [ ] Install it over the previous version on a device — it must update **in place**
- [ ] Launch the old build and confirm the in-app updater offers the new version
- [ ] If the release notes describe user-facing changes, mirror them in `CHANGELOG.md`

---

## 6. If the signing key is lost

Do **not** publish with a fresh key and present it as an update: Android will reject it
on existing installs, and the in-app updater refuses it before the installer opens.

Two honest options:

1. **Rotate with a lineage.** If the previous *private* key still exists somewhere, use
   `apksigner rotate --old-signer … --new-signer …` and sign the new APK with
   `--lineage`. Updates then continue to work.
2. **Clean install.** Publish with a new key, bump `applicationId` (for example
   `ai.arena.mobile.v2`), and document that users must uninstall the old app. The old
   package stops updating.

---

## 7. Rollback

A published GitHub release can be edited but not un-published for users who already
downloaded it. To pull an update from the feed:

1. Delete or mark the release as a draft (`gh release edit v1.0.4 --draft`). The updater
   only reads `releases/latest`, so a draft or pre-release is ignored.
2. Ship a higher `versionCode` with the fix; never reuse a version number.

---

## 8. What is *not* automated

- The keystore and its passwords (by design — never in CI variables or the repo).
- GPG signing of tags or checksums (no project key exists).
- Play Store publishing (this is a sideload-first project).
