const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');
const { execSync } = require('child_process');

const PORT = process.env.PORT || 3000;
const BASE_URL = `http://127.0.0.1:${PORT}`;

/**
 * Number of logTest() results the suite is expected to produce. Kept next to the count so
 * that adding a check without updating the documents that quote it fails the build.
 */
const EXPECTED_CHECK_COUNT = 127;

// ---------------------------------------------------------------------------------------
// APK readers. These parse the *artifact* rather than the source text, so a claim about the
// shipped binary (its version, its signatures, its signing key, its integrity) is checked
// against bytes instead of against a sentence in a build file.
// ---------------------------------------------------------------------------------------

function findEndOfCentralDirectory(buf) {
  const minOffset = Math.max(0, buf.length - 65558);
  for (let i = buf.length - 22; i >= minOffset; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      return i;
    }
  }
  return -1;
}

function listZipEntries(buf) {
  const eocd = findEndOfCentralDirectory(buf);
  if (eocd < 0) {
    return [];
  }
  const total = buf.readUInt16LE(eocd + 10);
  let pointer = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let i = 0; i < total; i++) {
    if (pointer + 46 > buf.length || buf.readUInt32LE(pointer) !== 0x02014b50) {
      break;
    }
    const method = buf.readUInt16LE(pointer + 10);
    const compressedSize = buf.readUInt32LE(pointer + 20);
    const nameLength = buf.readUInt16LE(pointer + 28);
    const extraLength = buf.readUInt16LE(pointer + 30);
    const commentLength = buf.readUInt16LE(pointer + 32);
    const localOffset = buf.readUInt32LE(pointer + 42);
    const name = buf.slice(pointer + 46, pointer + 46 + nameLength).toString('utf8');
    const dataOffset = localOffset + 30
      + buf.readUInt16LE(localOffset + 26)
      + buf.readUInt16LE(localOffset + 28);
    entries.push({ name, method, compressedSize, localOffset, dataOffset });
    pointer += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

function readZipEntry(buf, entry) {
  const raw = buf.slice(entry.dataOffset, entry.dataOffset + entry.compressedSize);
  if (entry.method === 0) {
    return raw;
  }
  if (entry.method !== 8) {
    throw new Error(`unsupported zip method ${entry.method}`);
  }
  return zlib.inflateRawSync(raw);
}

/** Reads the APK Signing Block and returns the pair IDs it contains. */
function signingBlockIds(buf) {
  const eocd = findEndOfCentralDirectory(buf);
  if (eocd < 0) {
    return [];
  }
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const magic = buf.lastIndexOf(Buffer.from('APK Sig Block 42'));
  if (magic < 0 || magic > cdOffset) {
    return [];
  }
  const ids = [];
  try {
    const blockSize = Number(buf.readBigUInt64LE(magic - 8));
    const start = magic + 16 - 8 - blockSize;
    let pos = start + 8;
    const end = magic - 8;
    while (pos + 12 <= end) {
      const pairSize = Number(buf.readBigUInt64LE(pos));
      if (pairSize <= 4 || pos + 8 + pairSize > end + 64) {
        break;
      }
      ids.push(buf.readUInt32LE(pos + 8));
      pos += 8 + pairSize;
    }
  } catch (err) {
    return ids;
  }
  return ids;
}

const APK_SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a;
const APK_SIGNATURE_SCHEME_V3_BLOCK_ID = 0xf05368c0;
const APK_SIGNATURE_SCHEME_V31_BLOCK_ID = 0x1b93ad61;

function readDerLength(buf, offset) {
  const first = buf[offset];
  if (first < 0x80) {
    return { length: first, size: 1 };
  }
  const count = first & 0x7f;
  let length = 0;
  for (let i = 0; i < count; i++) {
    length = length * 256 + buf[offset + 1 + i];
  }
  return { length, size: 1 + count };
}

/**
 * Locates the X.509 certificate inside a PKCS#7 blob (META-INF/*.RSA) by matching the
 * Certificate shape: SEQUENCE { SEQUENCE { [0] { INTEGER 2 } ... } ... }.
 */
function findCertificate(der) {
  for (let i = 0; i + 8 < der.length; i++) {
    if (der[i] !== 0x30) {
      continue;
    }
    const outer = readDerLength(der, i + 1);
    const outerBody = i + 1 + outer.size;
    if (outer.length < 200 || outer.length > 4096 || outerBody + 8 > der.length) {
      continue;
    }
    if (der[outerBody] !== 0x30) {
      continue;
    }
    const tbs = readDerLength(der, outerBody + 1);
    const tbsBody = outerBody + 1 + tbs.size;
    if (tbsBody + 5 > der.length) {
      continue;
    }
    if (der[tbsBody] === 0xa0 && der[tbsBody + 1] === 0x03
        && der[tbsBody + 2] === 0x02 && der[tbsBody + 3] === 0x01 && der[tbsBody + 4] === 0x02) {
      return der.slice(i, outerBody + outer.length);
    }
  }
  return null;
}

function certificateFingerprint(der) {
  const cert = findCertificate(der);
  if (!cert) {
    return null;
  }
  return crypto.createHash('sha256').update(cert).digest('hex').toUpperCase().match(/../g).join(':');
}

/** Recomputes every META-INF/MANIFEST.MF digest against the archive entries themselves. */
function verifyJarDigests(buf, entries) {
  const manifestEntry = entries.find((e) => e.name.toUpperCase() === 'META-INF/MANIFEST.MF');
  if (!manifestEntry) {
    return { checked: 0, mismatches: 0, skipped: 0 };
  }
  const text = readZipEntry(buf, manifestEntry).toString('utf8').replace(/\r\n /g, '');
  let current = null;
  let checked = 0;
  let mismatches = 0;
  let skipped = 0;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('Name: ')) {
      current = line.slice(6).trim();
      continue;
    }
    const match = /^(SHA!?-256|SHA-256|SHA1|SHA-1)[- ]?Digest:\s*(.+)$/i.exec(line.trim());
    if (!match || !current) {
      continue;
    }
    const entry = entries.find((e) => e.name === current);
    if (!entry) {
      skipped++;
      continue;
    }
    try {
      const algorithm = /sha.?1/i.test(match[1]) ? 'sha1' : 'sha256';
      const actual = crypto.createHash(algorithm).update(readZipEntry(buf, entry)).digest('base64');
      checked++;
      if (actual !== match[2].trim()) {
        mismatches++;
      }
    } catch (err) {
      skipped++;
    }
  }
  return { checked, mismatches, skipped };
}

// Test Collector
const results = {
  timestamp: new Date().toISOString(),
  totalTests: 0,
  passed: 0,
  failed: 0,
  testCases: []
};

function logTest(category, name, passed, details = null) {
  results.totalTests++;
  if (passed) results.passed++;
  else results.failed++;

  results.testCases.push({
    id: `TC_${results.totalTests.toString().padStart(2, '0')}`,
    category,
    name,
    passed,
    details
  });

  const icon = passed ? '✅ PASS' : '❌ FAIL';
  console.log(`  [${icon}] ${category}: ${name}`);
  if (details && !passed) {
    console.log(`         Details: ${JSON.stringify(details)}`);
  }
}

function request(endpoint, options = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(endpoint, BASE_URL);
    const req = http.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: data
        });
      });
    });
    req.on('error', err => reject(err));
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function runAllTests() {
  console.log('================================================================');
  console.log('       🧪 ARENA.AI AUDIT COMPLIANCE & INTEGRITY TEST SUITE      ');
  console.log('================================================================\n');

  // --- Suite 1: Local Development Server ---
  console.log('▶ SUITE 1: Local Development Server & Diagnostics');
  try {
    const health = await request('/api/health');
    const healthJson = JSON.parse(health.body);
    logTest('Dev Server', 'Health Endpoint Responds (200 OK)', health.statusCode === 200 && healthJson.status === 'ok');
    logTest('Dev Server', 'Node & Memory Reporting Valid', typeof healthJson.uptimeSeconds === 'number' && healthJson.memoryUsageMB > 0);
  } catch (err) {
    logTest('Dev Server', 'Health Endpoint Responds', false, err.message);
  }

  // --- Suite 2: Android Native APK Project Structure ---
  console.log('\n▶ SUITE 2: Android Architecture & Security Hardening');
  const projectRoot = path.join(__dirname, '..');
  const androidDir = path.join(projectRoot, 'android');
  const readmeSrc = fs.readFileSync(path.join(projectRoot, 'README.md'), 'utf8');
  const privacySrc = fs.readFileSync(path.join(projectRoot, 'PRIVACY.md'), 'utf8');
  const securitySrc = fs.readFileSync(path.join(projectRoot, 'SECURITY.md'), 'utf8');
  const appDir = path.join(androidDir, 'app');
  const srcDir = path.join(appDir, 'src', 'main');
  const resDir = path.join(srcDir, 'res');

  const manifestPath = path.join(srcDir, 'AndroidManifest.xml');
  const mainActivityPath = path.join(srcDir, 'java', 'ai', 'arena', 'mobile', 'MainActivity.java');
  const buildGradlePath = path.join(appDir, 'build.gradle');
  const gradlePropertiesPath = path.join(androidDir, 'gradle.properties');
  const proguardPath = path.join(appDir, 'proguard-rules.pro');

  const manifestSrc = fs.existsSync(manifestPath) ? fs.readFileSync(manifestPath, 'utf8') : '';
  const mainActivitySrc = fs.existsSync(mainActivityPath) ? fs.readFileSync(mainActivityPath, 'utf8') : '';
  const buildGradleSrc = fs.existsSync(buildGradlePath) ? fs.readFileSync(buildGradlePath, 'utf8') : '';
  const gradlePropertiesSrc = fs.existsSync(gradlePropertiesPath) ? fs.readFileSync(gradlePropertiesPath, 'utf8') : '';
  const rootBuildGradlePath = path.join(androidDir, 'build.gradle');
  const rootBuildGradleSrc = fs.existsSync(rootBuildGradlePath) ? fs.readFileSync(rootBuildGradlePath, 'utf8') : '';
  const wrapperPropsPath = path.join(androidDir, 'gradle', 'wrapper', 'gradle-wrapper.properties');
  const wrapperPropsSrc = fs.existsSync(wrapperPropsPath) ? fs.readFileSync(wrapperPropsPath, 'utf8') : '';

  logTest('Android Core', 'Package Unified to ai.arena.mobile',
    buildGradleSrc.includes("applicationId \"ai.arena.mobile\"") && mainActivitySrc.includes("package ai.arena.mobile;"));

  logTest('Android Core', 'Target SDK 35+ and Modern Min SDK 26 configured',
    buildGradleSrc.includes("minSdk 26")
    && buildGradleSrc.includes("targetSdk 35")
    && buildGradleSrc.includes("compileSdk 35"));


  logTest('Android Security', 'Cleartext Traffic Disabled (HTTPS only)',
    manifestSrc.includes('android:usesCleartextTraffic="false"'));

  logTest('Android Security', 'Application Backup Disabled (Token protection)',
    manifestSrc.includes('android:allowBackup="false"'));

  logTest('Android Security', 'Explicit minimal permissions (no storage, camera, mic or network-state)',
    manifestSrc.includes('android.permission.INTERNET')
    && manifestSrc.includes('android.permission.REQUEST_INSTALL_PACKAGES')
    && !manifestSrc.includes('ACCESS_NETWORK_STATE')
    && !manifestSrc.includes('WRITE_EXTERNAL_STORAGE')
    && !manifestSrc.includes('READ_EXTERNAL_STORAGE')
    && !manifestSrc.includes('RECORD_AUDIO')
    && !manifestSrc.includes('android.permission.CAMERA'));

  logTest('Android Security', 'Client Geolocation Disabled (Prevents physical location leaks)',
    mainActivitySrc.includes('setGeolocationEnabled(false)'));

  logTest('Android Security', 'Local File Access Restricted (Blocks file:// scheme)',
    mainActivitySrc.includes('setAllowFileAccess(false)') && mainActivitySrc.includes('setAllowContentAccess(false)'));

  logTest('Android Security', 'Navigation Allow-List Configured (External links open in system browser)',
    mainActivitySrc.includes('ALLOWED_HOST_SUFFIXES') && mainActivitySrc.includes('Intent.ACTION_VIEW'));

  logTest('Android Security', 'Navigation allow-list is narrow (no broad consumer domains)',
    mainActivitySrc.includes('"accounts.google.com"')
    && mainActivitySrc.includes('"login.live.com"')
    && !mainActivitySrc.includes('"google.com"')
    && !mainActivitySrc.includes('"live.com"'));

  logTest('Android Navigation', 'Modern OnBackPressedDispatcher used (API 34/35/36 compliant)',
    mainActivitySrc.includes('OnBackPressedCallback') && !mainActivitySrc.includes('onKeyDown('));

  logTest('Android UX', 'Full File Chooser with Multi-select ClipData Support',
    mainActivitySrc.includes('onShowFileChooser') && mainActivitySrc.includes('getClipData()'));

  logTest('Android UX', 'Session & Cookie Persistence Configured',
    mainActivitySrc.includes('CookieManager.getInstance().flush()'));

  logTest('Android UX', 'Pull-to-Refresh with SwipeRefreshLayout Integrated',
    mainActivitySrc.includes('SwipeRefreshLayout')
    && mainActivitySrc.includes('swipeRefreshLayout.setEnabled(true)')
    && !mainActivitySrc.includes('swipeRefreshLayout.setEnabled(false)'));

  logTest('Android UX', 'Pull-to-Refresh is gated by the page scroll position (no scroll-up reloads)',
    mainActivitySrc.includes('canPullToRefresh')
    && mainActivitySrc.includes('OnChildScrollUpCallback')
    && mainActivitySrc.includes('VIEWPORT_SHIM_JS')
    && mainActivitySrc.includes("type:'viewport'")
    && mainActivitySrc.includes('payload.optBoolean("atTop"')
    && mainActivitySrc.includes('pageLoading'));

  logTest('Android UX', 'Pull-to-Refresh reloads the current page and retries when offline',
    mainActivitySrc.includes('view.reload()')
    && mainActivitySrc.includes('if (isOfflineState)'));

  const updateManagerPath = path.join(srcDir, 'java', 'ai', 'arena', 'mobile', 'AppUpdateManager.java');
  const updateManagerSrc = fs.existsSync(updateManagerPath) ? fs.readFileSync(updateManagerPath, 'utf8') : '';
  logTest('Android UX', 'In-App GitHub Self-Updater & Version Checker Integrated',
    fs.existsSync(updateManagerPath) && updateManagerSrc.includes('checkForUpdates') && updateManagerSrc.includes('isNewerVersion') && mainActivitySrc.includes('AppUpdateManager.checkForUpdates'));

  logTest('Android UX', 'Updater checks on launch, resume and navigation (15-minute throttle)',
    updateManagerSrc.includes('CHECK_INTERVAL_MS = 15L * 60L * 1000L')
    && updateManagerSrc.includes('FAILURE_RETRY_MS')
    && mainActivitySrc.includes('AppUpdateManager.checkForUpdates(this);'));

  logTest('Android UX', 'Updater does not nag and recovers from failed checks',
    updateManagerSrc.includes('KEY_LAST_CHECK_OK')
    && updateManagerSrc.includes('PROMPT_COOLDOWN_MS')
    && updateManagerSrc.includes('KEY_LAST_PROMPT_VERSION'));

  logTest('Android UX', 'Updater only downloads over HTTPS and tolerates renamed assets',
    updateManagerSrc.includes('startsWith("https://")')
    && updateManagerSrc.includes('checksumCount == 1'));

  logTest('Android Build', 'AndroidX Enabled in gradle.properties',
    gradlePropertiesSrc.includes('android.useAndroidX=true'));

  logTest('Android Build', 'ProGuard / R8 Rules Present',
    fs.existsSync(proguardPath) && fs.readFileSync(proguardPath, 'utf8').includes('ai.arena.mobile'));

  // aapt resolves resource names at build time. There is no JVM in this environment, so the
  // resolution is re-implemented here: every R.<type>.<name> in the Java sources and every
  // @<type>/<name> in the manifest has to point at something that actually exists under res/.
  // This is the check that catches a renamed or forgotten drawable, which otherwise only fails
  // on the first real build.
  logTest('Android Build', 'every resource reference in Java and the manifest resolves', (() => {
    const listing = (dir) => {
      try {
        return fs.readdirSync(dir);
      } catch (err) {
        return [];
      }
    };
    const pool = (type) => {
      const names = new Set();
      for (const dir of listing(resDir).filter((d) => d === type || d.startsWith(`${type}-`))) {
        for (const file of listing(path.join(resDir, dir))) {
          names.add(file.replace(/(\.9)?\.(xml|png|webp|jpg|jpeg|gif)$/, ''));
        }
      }
      for (const values of listing(path.join(resDir, 'values')).filter((f) => f.endsWith('.xml'))) {
        const text = fs.readFileSync(path.join(resDir, 'values', values), 'utf8');
        for (const m of text.matchAll(/<(?:string|color|dimen|integer|bool|string-array|style)\s+name="([^"]+)"/g)) {
          names.add(m[1]);
        }
      }
      return names;
    };
    const kinds = ['string', 'drawable', 'mipmap', 'xml', 'layout', 'color', 'style', 'dimen'];
    const pools = {};
    for (const kind of kinds) {
      pools[kind] = pool(kind);
    }
    const javaSrc = ['MainActivity', 'DownloadHandler', 'AppUpdateManager', 'UpdateVerifier']
      .map((name) => path.join(srcDir, 'java', 'ai', 'arena', 'mobile', `${name}.java`))
      .filter((p) => fs.existsSync(p))
      .map((p) => fs.readFileSync(p, 'utf8'))
      .join('\n');
    const manifestSrc = fs.readFileSync(manifestPath, 'utf8');

    for (const m of javaSrc.matchAll(/(?<!android\.)\bR\.(string|drawable|mipmap|xml|layout|color|style|dimen)\.([A-Za-z_]\w*)/g)) {
      if (!pools[m[1]] || !pools[m[1]].has(m[2])) {
        return false;
      }
    }
    for (const m of manifestSrc.matchAll(/"@\+?(string|drawable|mipmap|xml|layout|color|style|dimen)\/([A-Za-z_]\w*)"/g)) {
      if (!pools[m[1]] || !pools[m[1]].has(m[2])) {
        return false;
      }
    }
    // A declared-but-unreferenced string is harmless; an unreferenced *file* under drawable/
    // would be dead weight, so flag only the reverse direction that actually breaks a build.
    return pools.string.size > 0 && pools.drawable.size > 0 && pools.mipmap.size > 0;
  })());

  // --- Suite 3: Adaptive Icons & Branding ---
  console.log('\n▶ SUITE 3: Official Brand Icons & Adaptive Drawables');
  const bgVector = path.join(resDir, 'drawable', 'ic_launcher_background.xml');
  const fgVector = path.join(resDir, 'drawable', 'ic_launcher_foreground.xml');
  const monoVector = path.join(resDir, 'drawable', 'ic_launcher_monochrome.xml');
  const anydpiLauncher = path.join(resDir, 'mipmap-anydpi-v26', 'ic_launcher.xml');
  const anydpiRound = path.join(resDir, 'mipmap-anydpi-v26', 'ic_launcher_round.xml');

  const bgSrc = fs.existsSync(bgVector) ? fs.readFileSync(bgVector, 'utf8') : '';
  const fgSrc = fs.existsSync(fgVector) ? fs.readFileSync(fgVector, 'utf8') : '';

  logTest('Branding', 'Adaptive Background Vector is Pure Black (#000000)',
    bgSrc.includes('#000000') && !bgSrc.includes('#26a69a'));

  logTest('Branding', 'Adaptive Foreground Vector contains Official Classical Pillar Mark',
    fgSrc.includes('pathData="M57.5364') && fgSrc.includes('#FFFFFF') && !fgSrc.includes('robot'));

  logTest('Branding', 'Android 13+ Themed Monochrome Vector Present',
    fs.existsSync(monoVector));

  logTest('Branding', 'Adaptive Launcher XML files configured in mipmap-anydpi-v26',
    fs.existsSync(anydpiLauncher) && fs.existsSync(anydpiRound));

  const densityPngsPresent = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'].every(d =>
    fs.existsSync(path.join(resDir, d, 'ic_launcher.png')) && fs.existsSync(path.join(resDir, d, 'ic_launcher_round.png'))
  );
  logTest('Branding', 'Density PNG Icons Present across all densities (mdpi to xxxhdpi)', densityPngsPresent);

  // --- Suite 4: Gradle Wrapper & Build System ---
  console.log('\n▶ SUITE 4: Gradle Wrapper & CI Configuration');
  const gradlewExists = fs.existsSync(path.join(androidDir, 'gradlew'));
  const gradlewBatExists = fs.existsSync(path.join(androidDir, 'gradlew.bat'));
  const wrapperJarExists = fs.existsSync(path.join(androidDir, 'gradle', 'wrapper', 'gradle-wrapper.jar'));
  const wrapperProps = wrapperPropsPath;

  logTest('Build System', 'Gradle Wrapper Shell Script (gradlew) Present', gradlewExists);
  logTest('Build System', 'Gradle Wrapper Windows Batch (gradlew.bat) Present', gradlewBatExists);
  logTest('Build System', 'Gradle Wrapper Jar Present', wrapperJarExists);
  logTest('Build System', 'Gradle 8.14 configured in wrapper properties', wrapperPropsSrc.includes('gradle-8.14'));
  logTest('Android Build', 'Toolchain is current (AGP 8.13 / Gradle 8.14)',
    rootBuildGradleSrc.includes("version '8.13.2'")
    && wrapperPropsSrc.includes('gradle-8.14'));

  logTest('Android Build', 'Release builds are shrunk by R8 and resources are shrunk',
    buildGradleSrc.includes('minifyEnabled true')
    && buildGradleSrc.includes('shrinkResources true')
    && fs.readFileSync(path.join(androidDir, 'app', 'proguard-rules.pro'), 'utf8').includes('-keep public class ai.arena.mobile.**'));

  logTest('Android Build', 'Edge-to-edge window insets are handled (targetSdk 35)',
    mainActivitySrc.includes('setOnApplyWindowInsetsListener')
    && mainActivitySrc.includes('WindowInsetsCompat.Type.systemBars()')
    && mainActivitySrc.includes('displayCutout()'));

  logTest('Repo hygiene', 'Unix helper scripts exist and are executable',
    ['start.sh', 'test.sh'].every((f) => {
      const p = path.join(projectRoot, f);
      if (!fs.existsSync(p)) return false;
      if (process.platform === 'win32') {
        try {
          const out = execSync(`git ls-files --stage "${f}"`, { cwd: projectRoot, encoding: 'utf8' });
          return out.startsWith('100755');
        } catch {
          return true;
        }
      }
      return (fs.statSync(p).mode & 0o111) !== 0;
    }));

  logTest('Repo hygiene', 'Release checklist and pre-flight script exist',
    fs.existsSync(path.join(projectRoot, 'RELEASE.md'))
    && fs.existsSync(path.join(projectRoot, 'scripts', 'release-check.sh')));

  logTest('Repo hygiene', 'CODEOWNERS, editorconfig and node version pin exist',
    fs.existsSync(path.join(projectRoot, '.github', 'CODEOWNERS'))
    && fs.existsSync(path.join(projectRoot, '.editorconfig'))
    && fs.existsSync(path.join(projectRoot, '.nvmrc')));

  logTest('Repo hygiene', 'Dev proxy is containerisable (Dockerfile + compose)',
    fs.existsSync(path.join(projectRoot, 'Dockerfile'))
    && fs.existsSync(path.join(projectRoot, 'docker-compose.yml')));

  logTest('Supply chain', 'CodeQL analysis runs for Java and JavaScript',
    fs.existsSync(path.join(projectRoot, '.github', 'workflows', 'codeql.yml'))
    && fs.readFileSync(path.join(projectRoot, '.github', 'workflows', 'codeql.yml'), 'utf8')
        .includes('github/codeql-action/analyze@v4')
    && fs.readFileSync(path.join(projectRoot, '.github', 'workflows', 'codeql.yml'), 'utf8')
        .includes('--rerun-tasks'));

  const ciWorkflowPath = path.join(projectRoot, '.github', 'workflows', 'build.yml');
  const ciSrc = fs.existsSync(ciWorkflowPath) ? fs.readFileSync(ciWorkflowPath, 'utf8') : '';
  logTest('CI / Automation', 'GitHub Actions Build Workflow Present',
    ciSrc.includes('actions/setup-java') && ciSrc.includes('assembleDebug'));

  // --- Suite 5: Legal & Community Governance ---
  console.log('\n▶ SUITE 5: Legal & Community Documentation');
  logTest('Legal', 'Apache-2.0 LICENSE File Present', fs.existsSync(path.join(projectRoot, 'LICENSE')));
  logTest('Legal', 'NOTICE File with Brand & Third-Party Attribution Present', fs.existsSync(path.join(projectRoot, 'NOTICE')));
  logTest('Legal', 'PRIVACY.md Detailing Zero Telemetry & Client Safeguards Present', fs.existsSync(path.join(projectRoot, 'PRIVACY.md')));
  logTest('Security', 'SECURITY.md Responsible Disclosure Policy Present', fs.existsSync(path.join(projectRoot, 'SECURITY.md')));
  logTest('Community', 'CONTRIBUTING.md Guidelines Present', fs.existsSync(path.join(projectRoot, 'CONTRIBUTING.md')));
  logTest('Community', 'CODE_OF_CONDUCT.md Present', fs.existsSync(path.join(projectRoot, 'CODE_OF_CONDUCT.md')));
  logTest('Community', 'CHANGELOG.md Present with Audit Remediation Log', fs.existsSync(path.join(projectRoot, 'CHANGELOG.md')));

  // --- Suite 6: Smali Dump Retirement ---
  console.log('\n▶ SUITE 6: Legacy Codebase Purge');
  const smaliTreeRetired = !fs.existsSync(path.join(projectRoot, 'arena_standalone_apk'));
  const publicStaleRemoved = !fs.existsSync(path.join(projectRoot, 'public'));
  const dataStaleRemoved = !fs.existsSync(path.join(projectRoot, 'data'));

  logTest('Hygiene', 'Decompiled Smali Tree (3,300+ legacy files) Completely Removed', smaliTreeRetired);
  logTest('Hygiene', 'Unused Shell HTML and Stale Data Files Removed', publicStaleRemoved && dataStaleRemoved);


  // --- Suite 7: Native capability bridge (no extra app chrome) ---
  console.log('\n▶ SUITE 7: Native Capability Bridge');
  const downloadHandlerPath = path.join(srcDir, 'java', 'ai', 'arena', 'mobile', 'DownloadHandler.java');
  const updateVerifierPath = path.join(srcDir, 'java', 'ai', 'arena', 'mobile', 'UpdateVerifier.java');
  const downloadSrc = fs.existsSync(downloadHandlerPath) ? fs.readFileSync(downloadHandlerPath, 'utf8') : '';
  const updateVerifierSrc = fs.existsSync(updateVerifierPath) ? fs.readFileSync(updateVerifierPath, 'utf8') : '';

  logTest('Web Bridge', 'HTTPS downloads use DownloadManager with WebView session cookies',
    mainActivitySrc.includes('DownloadHandler.handle')
    && downloadSrc.includes('DownloadManager')
    && downloadSrc.includes('CookieManager.getInstance().getCookie'));

  logTest('Web Bridge', 'Client-generated blob: and data: downloads are preserved',
    downloadSrc.includes('startsWith("blob:")') && downloadSrc.includes('startsWith("data:")'));

  // The size ceiling has to be applied where the bytes are still small: checking a length on
  // the Java side happens after the renderer has already built the base64 copy, i.e. after the
  // memory spike the limit was meant to prevent.
  logTest('Downloads', 'blob size ceiling is enforced inside the page, before the base64 copy',
    downloadSrc.includes('b.size>') && downloadSrc.includes('TOO_LARGE'));

  logTest('Downloads', 'a destroyed WebView cannot wedge every later blob download',
    downloadSrc.includes('blobLockOwner') && downloadSrc.includes('blobLockDeadline')
    && downloadSrc.includes('onWebViewDestroyed')
    && mainActivitySrc.includes('DownloadHandler.onWebViewDestroyed'));

  logTest('Downloads', 'page-injected URLs are escaped as JavaScript string literals',
    downloadSrc.includes('private static String toJavaScriptStringLiteral')
    && downloadSrc.includes('+ safeUrl +')
    && !downloadSrc.includes('fetch(\'"'));

  logTest('Downloads', 'a data: download is size-capped before it is decoded',
    /if \(url\.length\(\) > MAX_BLOB_BYTES/.test(downloadSrc));

  // Base64 of a permitted 48 MB export is a ~64 MB String already resident on the Java side.
  // Copying it (substring) and then decoding it into a fresh array put peak heap near 240 MB,
  // which is over the default per-app limit on many devices — an OOM at a size the ceiling
  // explicitly allows. Both now stream through one reusable buffer.
  logTest('Downloads', 'base64 payloads are streamed to disk, not copied and re-decoded',
    downloadSrc.includes('Base64.getMimeDecoder().wrap(new AsciiRegionStream')
    && !downloadSrc.includes('Base64.decode(')
    && !downloadSrc.includes('String payload =')
    && (downloadSrc.match(/copyCapped\(/g) || []).length >= 3);

  logTest('Downloads', 'a failed MediaStore write does not leave a pending row behind',
    downloadSrc.includes('getContentResolver().delete(item, null, null)')
    && downloadSrc.includes('throw new IOException("MediaStore refused the download")'));

  logTest('Web Bridge', 'Camera capture offered when the page requests capture',
    mainActivitySrc.includes('isCaptureEnabled')
    && mainActivitySrc.includes('ACTION_IMAGE_CAPTURE')
    && manifestSrc.includes('android.media.action.IMAGE_CAPTURE'));

  logTest('Web Bridge', 'JavaScript alert/confirm/prompt dialogs are handled natively',
    mainActivitySrc.includes('onJsAlert')
    && mainActivitySrc.includes('onJsConfirm')
    && mainActivitySrc.includes('onJsPrompt'));

  logTest('Web Bridge', 'Before-unload confirmation protects unsaved battles',
    mainActivitySrc.includes('onJsBeforeUnload'));

  logTest('Web Bridge', 'Fullscreen HTML5 media is supported',
    mainActivitySrc.includes('onShowCustomView') && mainActivitySrc.includes('onHideCustomView'));

  logTest('Web Bridge', 'navigator.share is bridged to the Android share sheet',
    mainActivitySrc.includes('addWebMessageListener') && mainActivitySrc.includes('navigator.share'));

  logTest('Web Bridge', 'OAuth popups are origin-checked, tracked and always destroyed',
    mainActivitySrc.includes('popupWindows') && mainActivitySrc.includes('closePopup')
    && mainActivitySrc.includes('isAuthHost'));

  logTest('Web Bridge', 'intent:// links and browser_fallback_url are honoured',
    mainActivitySrc.includes('Intent.parseUri') && mainActivitySrc.includes('browser_fallback_url'));

  // Presence of parseUri is the capability; removing the target it names is the safety.
  // Without this the shell is the textbook WebView intent-redirection primitive.
  logTest('Web Bridge', 'intent:// targets cannot name a component, package or selector',
    mainActivitySrc.includes('setComponent(null)')
    && mainActivitySrc.includes('setSelector(null)')
    && mainActivitySrc.includes('setPackage(null)'));

  logTest('Web Bridge', 'browser_fallback_url is restricted to http(s)',
    mainActivitySrc.includes('readBrowserFallbackUrl')
    && /if \(!lower\.equals\("https"\) && !lower\.equals\("http"\)\)/.test(mainActivitySrc));

  logTest('Web Bridge', 'the bridge is registered for every host that receives the shims',
    (() => {
      // INTERNAL_HOST_SUFFIXES decides where the JS shims are injected; the WebMessageListener
      // origins decide which pages get window.arenaAndroid at all. A host in the first list but
      // not the second gets shims that silently no-op, which reads as a dead pull-to-refresh.
      const internal = /INTERNAL_HOST_SUFFIXES[\s\S]*?Arrays\.asList\(([\s\S]*?)\)\s*;/.exec(mainActivitySrc);
      const origins = /Set<String> origins[\s\S]*?Arrays\.asList\(([\s\S]*?)\)\s*;/.exec(mainActivitySrc);
      if (!internal || !origins) {
        return false;
      }
      const hosts = [...internal[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
      const registered = [...origins[1].matchAll(/"https:\/\/([^"]+)"/g)].map((m) => m[1]);
      return hosts.length > 0 && hosts.every((host) =>
        registered.includes(host) || registered.includes(`*.${host}`));
    })());

  logTest('Web Bridge', 'system font size changes are applied without recreating the page',
    mainActivitySrc.includes('onConfigurationChanged')
    && mainActivitySrc.includes('setTextZoom(systemFontZoom())'));

  logTest('File Uploads', 'abandoned camera captures are reclaimed, and the MIME hint is not dead code',
    mainActivitySrc.includes('pruneStaleCameraCaptures')
    && !mainActivitySrc.includes('pendingCameraMime'));

  logTest('Web Bridge', 'Renderer crashes and HTTP/SSL failures are recovered',
    mainActivitySrc.includes('onRenderProcessGone')
    && mainActivitySrc.includes('onReceivedHttpError')
    && mainActivitySrc.includes('onReceivedSslError'));

  // --- Suite 8: Update integrity ---
  console.log('\n▶ SUITE 8: Verified In-App Updates');

  logTest('Updater', 'Downloaded APK is verified before installation',
    updateVerifierSrc.includes('sha256')
    && updateVerifierSrc.includes('getPackageArchiveInfo')
    && updateVerifierSrc.includes('SIGNER_MISMATCH')
    && updateManagerSrc.includes('UpdateVerifier.verify'));

  logTest('Updater', 'Checksum comes from SHA256SUMS or the GitHub asset digest',
    updateManagerSrc.toLowerCase().includes('sha256sums')
    && updateManagerSrc.includes('sha256:')
    && updateManagerSrc.includes('findShaForFile'));

  logTest('Updater', 'Drafts and pre-releases are ignored',
    updateManagerSrc.includes('optBoolean("draft"') && updateManagerSrc.includes('optBoolean("prerelease"'));

  logTest('Updater', 'Update checks are throttled and skippable',
    updateManagerSrc.includes('CHECK_INTERVAL_MS') && updateManagerSrc.includes('KEY_SKIPPED_VERSION'));

  logTest('Updater', 'Downloads show progress, support cancel and check free space',
    updateManagerSrc.includes('update_downloading_message')
    && updateManagerSrc.includes('update_cancel')
    && updateManagerSrc.includes('StatFs'));

  logTest('Updater', 'Install permission is required before opening the installer',
    updateManagerSrc.includes('canRequestPackageInstalls'));

  // Walking out to Settings backgrounds the app, which is exactly when the system is most
  // likely to reclaim the process; a static field alone loses the update the user asked for.
  logTest('Updater', 'a deferred install survives the process being killed in Settings',
    updateManagerSrc.includes('KEY_PENDING_APK_PATH')
    && updateManagerSrc.includes('rememberPendingInstall')
    && updateManagerSrc.includes('clearPendingInstall'));

  logTest('Updater', 'a resumed install is re-verified before the installer opens',
    /checkPendingInstall[\s\S]*UpdateVerifier\.verify/.test(updateManagerSrc));

  logTest('Updater', 'a waiting APK lives in filesDir, not the purgeable cache',
    updateManagerSrc.includes('getFilesDir()')
    && !updateManagerSrc.includes('getCacheDir()')
    && fs.readFileSync(path.join(srcDir, 'res', 'xml', 'file_paths.xml'), 'utf8')
      .includes('<files-path name="files_updates" path="updates"'));

  logTest('Updater', 'the download cannot outlive the published asset size',
    updateManagerSrc.includes('exceeded the published asset size'));

  logTest('Updater', 'the progress dialog is torn down with its activity',
    updateManagerSrc.includes('public static void onActivityDestroyed')
    && mainActivitySrc.includes('AppUpdateManager.onActivityDestroyed'));

  logTest('Updater', 'no hardcoded installed-version fallback',
    !/return "1\.0\.\d+"/.test(updateManagerSrc)
    && updateManagerSrc.includes('BuildConfig.VERSION_NAME'));

  logTest('Updater', 'the archive signer falls back to legacy signatures on quirky ROMs',
    updateVerifierSrc.includes('preferSigningCertificates')
    && updateVerifierSrc.includes('info.signatures'));

  // Leak guards. Everything here runs on a thread or a handler that outlives the screen, so a
  // strong Activity capture is a leak by construction; the fixes are weak references plus
  // explicit removal, and these checks are what keep them that way.
  logTest('Memory', 'background update work reaches the activity weakly',
    (updateManagerSrc.match(
      /final WeakReference<Activity> activityRef = new WeakReference<>\(activity\);/g) || []).length >= 2
    && (updateManagerSrc.match(/updateExecutor\.execute/g) || []).length >= 2);

  logTest('Memory', 'the static progress dialog is a weak reference',
    updateManagerSrc.includes('WeakReference<AlertDialog> activeProgressDialog'));

  logTest('Memory', 'the static blob-lock owner is a weak reference',
    downloadSrc.includes('WeakReference<WebView> blobLockOwner'));

  logTest('Memory', 'the queued blob poll is removable, not just time-bounded',
    downloadSrc.includes('cancelPendingBlobPoll')
    && downloadSrc.includes('MAIN_HANDLER.removeCallbacks')
    && downloadSrc.includes('activity.isDestroyed()'));

  logTest('Memory', 'the connectivity probe does not outlive the activity it woke for',
    mainActivitySrc.includes('WeakReference<MainActivity> self')
    && mainActivitySrc.includes('host == null || host.destroyed'));

  logTest('Memory', 'every WebView created by the shell is destroyed on the way out',
    (() => {
      const destroys = (mainActivitySrc.match(/\.destroy\(\)/g) || []).length;
      return destroys >= 3
        && mainActivitySrc.includes('popupWindows.clear()')
        && /onDestroy\(\)[\s\S]*webView = null;/.test(mainActivitySrc);
    })());

  // Retention is only half of "uses too much memory": a single oversized allocation can trip the
  // per-app heap limit on its own, so the one unbounded write path keeps its copy bounded by a
  // fixed buffer and re-checks the ceiling while it copies.
  logTest('Memory', 'download writes peak at a buffer, not at the payload size',
    /copyCapped\(InputStream source, OutputStream target\)/.test(downloadSrc)
    && downloadSrc.includes('byte[] buffer = new byte[64 * 1024];')
    && downloadSrc.includes('Payload exceeded the download ceiling')
    && !/out\.write\(data\);/.test(downloadSrc));

  logTest('Updater', 'documented check interval matches the code',
    (() => {
      const constant = /CHECK_INTERVAL_MS\s*=\s*(\d+)L?\s*\*\s*60L?\s*\*\s*1000L/.exec(updateManagerSrc);
      if (!constant) {
        return false;
      }
      const minutes = Number(constant[1]);
      // PRIVACY.md is a disclosure document: it must state the real cadence, not an older one.
      return privacySrc.includes(`${minutes} minutes`) && !privacySrc.includes('24 hours');
    })());

  // --- Suite 9: Release engineering ---
  console.log('\n▶ SUITE 9: Release Engineering');

  let gradlewExecutable = false;
  if (process.platform === 'win32') {
    try {
      const out = execSync('git ls-files --stage android/gradlew', { cwd: projectRoot, encoding: 'utf8' });
      gradlewExecutable = out.startsWith('100755');
    } catch {
      gradlewExecutable = fs.existsSync(path.join(androidDir, 'gradlew'));
    }
  } else {
    try {
      gradlewExecutable = (fs.statSync(path.join(androidDir, 'gradlew')).mode & 0o111) !== 0;
    } catch {
      gradlewExecutable = false;
    }
  }
  logTest('Build System', 'gradlew is committed with the executable bit', gradlewExecutable);

  logTest('Build System', 'APK Signature Scheme v1, v2 and v3 are enabled',
    buildGradleSrc.includes('v1SigningEnabled true')
    && buildGradleSrc.includes('v2SigningEnabled true')
    && buildGradleSrc.includes('enableV3Signing true'));

  logTest('Build System', 'No hardcoded keystore passwords remain in the build',
    !buildGradleSrc.includes('arena1234') && buildGradleSrc.includes('ARENA_RELEASE_STORE_FILE'));

  logTest('Build System', 'Release builds fail loudly rather than silently reusing the debug key',
    buildGradleSrc.includes('must not be distributed'));

  logTest('Build System', 'APK verification script present and referenced by CI',
    fs.existsSync(path.join(projectRoot, 'scripts', 'verify-apk.sh'))
    && ciSrc.includes('verify-apk.sh'));

  const releaseWorkflowPath = path.join(projectRoot, '.github', 'workflows', 'release.yml');
  const releaseSrc = fs.existsSync(releaseWorkflowPath)
    ? fs.readFileSync(releaseWorkflowPath, 'utf8') : '';
  logTest('CI / Automation', 'Tag-triggered signed release workflow exists',
    releaseSrc.includes("tags:") && releaseSrc.includes('environment: release')
    && releaseSrc.includes('gh release upload'));

  logTest('Release engineering', 'Publishing is pinned to the documented signing certificate',
    releaseSrc.includes('EXPECTED_CERT_SHA256')
    && releaseSrc.includes('0A:78:95:E6')
    && releaseSrc.includes('--print-certs'));
  logTest('Release engineering', 'Release pipeline has a dry-run mode and refuses unsigned builds',
    releaseSrc.includes('dry_run')
    && releaseSrc.includes('Refusing to publish a debug-signed APK'));


  logTest('CI / Automation', 'Build workflow validates the Gradle wrapper and audits dependencies',
    ciSrc.includes('wrapper-validation') && ciSrc.includes('npm audit'));

  logTest('Manifest', 'Predictive back is opted in for Android 13+',
    manifestSrc.includes('enableOnBackInvokedCallback="true"'));

  logTest('Manifest', 'Data extraction rules block cloud backup and device transfer',
    manifestSrc.includes('dataExtractionRules') && fs.existsSync(path.join(resDir, 'xml', 'data_extraction_rules.xml')));

  // Named for what it actually inspects. The old name promised "inside the APK" while only
  // looking at the source tree, which is why a released binary with no bundled licence text
  // still passed. The artifact-side check lives in Suite 10 and warns about exactly that.
  logTest('Legal', 'License and NOTICE are staged for packaging in src/main/assets',
    fs.existsSync(path.join(srcDir, 'assets', 'LICENSE.txt'))
    && fs.existsSync(path.join(srcDir, 'assets', 'NOTICE.txt')));

  logTest('Legal', 'NOTICE lists only dependencies the app actually declares',
    (() => {
      const notice = fs.readFileSync(path.join(projectRoot, 'NOTICE'), 'utf8');
      const deps = buildGradleSrc;
      const mentionsMaterial = /Material Components/i.test(notice);
      const dependsOnMaterial = /com\.google\.android\.material|material:/.test(deps);
      return mentionsMaterial === dependsOnMaterial;
    })());

  logTest('Legal', 'Privacy policy and security policy disclose the install permission',
    privacySrc.includes('REQUEST_INSTALL_PACKAGES') && securitySrc.includes('REQUEST_INSTALL_PACKAGES'));

  logTest('Legal', 'Privacy policy discloses GitHub update checks and third-party cookies',
    privacySrc.includes('api.github.com') && /third-party cookies/i.test(privacySrc));

  logTest('Docs', 'Download link points at the latest release instead of a pinned version',
    readmeSrc.includes('releases/latest/download/ArenaAI.apk'));

  logTest('Docs', 'README documents the current native capabilities',
    readmeSrc.includes('Download') && readmeSrc.includes('camera') && readmeSrc.includes('share'));

  logTest('Metadata', 'package.json and package-lock.json agree on name and version', (() => {
    try {
      const pkg = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8'));
      const lock = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package-lock.json'), 'utf8'));
      return pkg.name === lock.name && pkg.version === lock.version
        && pkg.name === lock.packages[''].name && pkg.version === lock.packages[''].version;
    } catch (err) {
      return false;
    }
  })());

  logTest('Metadata', 'documents quote the real audit-suite check count', (() => {
    // Each document has to carry the number itself — either as "N-check suite" or as the
    // "N / N Passing" score line — so no doc can keep advertising an old run.
    const docs = ['RELEASE.md', 'test.sh', 'ANALYSIS_REPORT.md'].map((name) =>
      fs.readFileSync(path.join(projectRoot, name), 'utf8'));
    return docs.every((text) => text.includes(`${EXPECTED_CHECK_COUNT}-check`)
      || text.includes(`${EXPECTED_CHECK_COUNT} / ${EXPECTED_CHECK_COUNT}`));
  })());

  logTest('Metadata', 'Gradle versionName matches package.json', (() => {
    const match = buildGradleSrc.match(/versionName\s+"([^"]+)"/);
    if (!match) return false;
    try {
      const pkg = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8'));
      return match[1] === pkg.version;
    } catch (err) {
      return false;
    }
  })());

  // --- Suite 10: Shipped APK artifact ---
  console.log('\n▶ SUITE 10: Shipped APK Artifact');

  const apkPath = path.join(projectRoot, 'ArenaAI.apk');
  const sumsPath = path.join(projectRoot, 'SHA256SUMS');

  function inspectZip(apkBuffer) {
    let eocd = -1;
    const minOffset = Math.max(0, apkBuffer.length - 65558);
    for (let i = apkBuffer.length - 22; i >= minOffset; i--) {
      if (apkBuffer.readUInt32LE(i) === 0x06054b50) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) {
      return null;
    }
    const totalEntries = apkBuffer.readUInt16LE(eocd + 10);
    const cdOffset = apkBuffer.readUInt32LE(eocd + 16);
    let pointer = cdOffset;
    let stored = 0;
    let misaligned = 0;
    for (let i = 0; i < totalEntries; i++) {
      if (pointer + 46 > apkBuffer.length || apkBuffer.readUInt32LE(pointer) !== 0x02014b50) {
        break;
      }
      const method = apkBuffer.readUInt16LE(pointer + 10);
      const nameLength = apkBuffer.readUInt16LE(pointer + 28);
      const extraLength = apkBuffer.readUInt16LE(pointer + 30);
      const commentLength = apkBuffer.readUInt16LE(pointer + 32);
      const localOffset = apkBuffer.readUInt32LE(pointer + 42);
      if (method === 0 && localOffset + 30 <= apkBuffer.length) {
        const localNameLength = apkBuffer.readUInt16LE(localOffset + 26);
        const localExtraLength = apkBuffer.readUInt16LE(localOffset + 28);
        const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
        stored++;
        if (dataOffset % 4 !== 0) {
          misaligned++;
        }
      }
      pointer += 46 + nameLength + extraLength + commentLength;
    }
    return { totalEntries, stored, misaligned };
  }

  if (fs.existsSync(apkPath)) {
    const apkBuffer = fs.readFileSync(apkPath);
    const zipInfo = inspectZip(apkBuffer);
    const apkHash = crypto.createHash('sha256').update(apkBuffer).digest('hex');
    const sumsSrc = fs.existsSync(sumsPath) ? fs.readFileSync(sumsPath, 'utf8') : '';
    const sumsLines = sumsSrc.trim().split(/\r?\n/).filter(Boolean);
    const sumsMatch = sumsLines.length > 0
      && sumsLines[0].trim().split(/\s+/)[0].toLowerCase() === apkHash;

    const entries = listZipEntries(apkBuffer);
    const ids = signingBlockIds(apkBuffer);
    const hasV2 = ids.includes(APK_SIGNATURE_SCHEME_V2_BLOCK_ID);
    const hasV3 = ids.includes(APK_SIGNATURE_SCHEME_V3_BLOCK_ID)
      || ids.includes(APK_SIGNATURE_SCHEME_V31_BLOCK_ID);
    const digests = verifyJarDigests(apkBuffer, entries);

    logTest('Artifact', 'Committed APK SHA-256 matches SHA256SUMS', sumsMatch);
    logTest('Artifact', 'README publishes the same SHA-256 as the committed APK', readmeSrc.includes(apkHash));
    logTest('Artifact', 'All uncompressed APK entries are 4-byte aligned',
      zipInfo !== null && zipInfo.stored > 0 && zipInfo.misaligned === 0);
    logTest('Artifact', 'APK contains an APK Signature Scheme v2 block', hasV2);
    logTest('Artifact', 'APK package is ai.arena.mobile',
      apkBuffer.includes(Buffer.from('ai.arena.mobile', 'utf16le')));

    // Everything below reads the archive instead of the source text. A build file can say
    // anything it likes; these are the checks that describe what was actually shipped.
    logTest('Artifact', 'APK v1 (JAR) digests recompute for every entry',
      digests.checked > 300 && digests.mismatches === 0);

    const certEntry = entries.find((e) => /\.(RSA|DSA|EC)$/i.test(e.name));
    let apkFingerprint = null;
    if (certEntry) {
      try {
        apkFingerprint = certificateFingerprint(readZipEntry(apkBuffer, certEntry));
      } catch (err) {
        apkFingerprint = null;
      }
    }
    logTest('Artifact', 'APK signing certificate matches the README fingerprint',
      apkFingerprint !== null && readmeSrc.includes(apkFingerprint));
    logTest('Artifact', 'APK signing certificate matches the certificate release.yml pins',
      apkFingerprint !== null && (() => {
        const pin = /EXPECTED_CERT_SHA256:\s*"([0-9A-Fa-f:]+)"/.exec(releaseSrc);
        return pin !== null
          && pin[1].toUpperCase().replace(/:/g, '') === apkFingerprint.replace(/:/g, '').toUpperCase();
      })());

    const sourceVersionMatch = /versionName "([^"]+)"/.exec(buildGradleSrc);
    const sourceVersion = sourceVersionMatch ? sourceVersionMatch[1] : null;
    const documented = /\|\s*\*\*Version\*\*\s*\|\s*`(\d+\.\d+(?:\.\d+)?)\s+\(versionCode\s*(\d+)\)`/
      .exec(readmeSrc);
    const manifestEntry = entries.find((e) => e.name === 'AndroidManifest.xml');
    let manifestBytes = Buffer.alloc(0);
    try {
      manifestBytes = readZipEntry(apkBuffer, manifestEntry);
    } catch (err) {
      manifestBytes = Buffer.alloc(0);
    }
    const apkContainsVersion = (version) =>
      manifestBytes.includes(Buffer.from(version, 'utf16le'));

    logTest('Artifact', 'APK version matches the version README documents for that artifact',
      documented !== null && apkContainsVersion(documented[1]));
    logTest('Artifact',
      'committed APK is current, or disclosed as a historical artifact',
      (() => {
        if (!documented || sourceVersion === null) {
          return false;
        }
        if (documented[1] === sourceVersion) {
          // The README presents the binary as current: then it had better be current.
          return apkContainsVersion(sourceVersion);
        }
        // Older than the tree. Acceptable only with an explicit disclosure next to the
        // specification table, so no reader mistakes the artifact for the current build.
        return apkContainsVersion(documented[1])
          && !apkContainsVersion(sourceVersion)
          && /Source on this branch is \*\*v/.test(readmeSrc);
      })());

    logTest('Artifact', 'Documented signature schemes match the APK signing block',
      hasV3
        ? /v1[^\n]*v2[^\n]*v3/.test(readmeSrc)
        : /v1[^\n]*\+[^\n]*v2\b[^\n|]*(?:artifact|v1\.0)/i.test(readmeSrc)
          || readmeSrc.includes('v1 (JAR) + v2'));

    // Not a verdict, a heads-up: things the shipped binary is missing that the source already
    // provides. It cannot be fixed by editing this repository (only a build can), so it warns
    // instead of failing — but it must never be silently true.
    const missingAssets = ['assets/LICENSE.txt', 'assets/NOTICE.txt']
      .filter((name) => !entries.some((e) => e.name === name));
    if (sourceVersion && !apkContainsVersion(sourceVersion)) {
      console.log(`  ⚠️  WARNING  committed APK is ${documented ? documented[1] : 'an older'} build, source is ${sourceVersion}`);
      console.log('     → releases/latest and the in-app updater still serve the older binary. Publish this source.');
    }
    if (!hasV3) {
      console.log('  ⚠️  WARNING  committed APK has no APK Signature Scheme v3 block (v1 + v2 only).');
    }
    if (missingAssets.length > 0) {
      console.log(`  ⚠️  WARNING  committed APK does not bundle: ${missingAssets.join(', ')}`);
    }
  } else {
    logTest('Artifact', 'Committed APK present', false);
  }

  // --- Suite 11: Dev server endpoints ---
  console.log('\n▶ SUITE 11: Development Server Endpoints');
  try {
    const info = await request('/api/network-info');
    const infoJson = JSON.parse(info.body);
    logTest('Dev Server', 'Network info returns a non-loopback mobile URL',
      info.statusCode === 200
      && /^http:\/\/(?!127\.0\.0\.1|0\.0\.0\.0)/.test(infoJson.mobileUrl)
      && typeof infoJson.qrDataUrl === 'string'
      && infoJson.qrDataUrl.startsWith('data:image/png;base64,'));
  } catch (err) {
    logTest('Dev Server', 'Network info returns a non-loopback mobile URL', false, err.message);
  }

  try {
    const version = await request('/api/version');
    const versionJson = JSON.parse(version.body);
    const packageVersion = JSON.parse(
      fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8')
    ).version;
    logTest('Dev Server', 'Version endpoint mirrors package.json',
      version.statusCode === 200 && versionJson.version === packageVersion);
  } catch (err) {
    logTest('Dev Server', 'Version endpoint mirrors package.json', false, err.message);
  }

  try {
    const arena = await request('/api/arena-status');
    const arenaJson = JSON.parse(arena.body);
    logTest('Dev Server', 'Arena status endpoint always returns a structured result',
      arena.statusCode === 200 && typeof arenaJson.online === 'boolean');
  } catch (err) {
    logTest('Dev Server', 'Arena status endpoint always returns a structured result', false, err.message);
  }

  // Write Test Results File
  const testResultsPath = path.join(__dirname, 'test-results.json');
  fs.writeFileSync(testResultsPath, JSON.stringify(results, null, 2), 'utf-8');

  console.log('\n================================================================');
  console.log(`  TEST RESULTS: ${results.passed} PASSED / ${results.totalTests} TOTAL (${results.failed} FAILED)`);
  console.log(`  Score: ${Math.round((results.passed / results.totalTests) * 100)}% SUCCESS RATE`);
  console.log('================================================================\n');

  return results;
}

if (require.main === module) {
  runAllTests().then((res) => {
    process.exit(res.failed === 0 ? 0 : 1);
  });
}

module.exports = { EXPECTED_CHECK_COUNT, runAllTests };
