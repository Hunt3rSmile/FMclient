const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path    = require('path');
const os      = require('os');
const { execFile, exec, spawn, spawnSync } = require('child_process');
const fs      = require('fs');
const https   = require('https');
const http    = require('http');
const zlib    = require('zlib');
const crypto  = require('crypto');

const GITHUB_REPO    = 'Hunt3rSmile/FMclient';
const CURRENT_VER    = app.getVersion();
const FMCLIENT_UA    = `FMclient/${CURRENT_VER} (${process.platform}; ${os.arch()})`;

let mainWindow;
let detectedJavaPath = null;
let gameProcess      = null;
const updateState    = {
  info: null,
  downloading: false,
  installing: false,
  downloadedPath: null,
};

// ── Auth storage ──────────────────────────────────────────────
const authFile = path.join(app.getPath('userData'), 'fmclient_auth.json');

function loadAuth() {
  try { return JSON.parse(fs.readFileSync(authFile, 'utf8')); }
  catch { return null; }
}
function saveAuth(data) { fs.writeFileSync(authFile, JSON.stringify(data, null, 2)); }
function clearAuth()    { try { fs.unlinkSync(authFile); } catch {} }

// ── Auto-update via GitHub Releases ──────────────────────────
function semverGt(a, b) {
  // returns true if version string a > b  (e.g. "1.0.1" > "1.0.0")
  const pa = a.replace(/^v/, '').split('.').map(Number);
  const pb = b.replace(/^v/, '').split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    if ((pa[i] || 0) > (pb[i] || 0)) return true;
    if ((pa[i] || 0) < (pb[i] || 0)) return false;
  }
  return false;
}

function sendUpdateStatus(payload) {
  try {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update-status', payload);
    }
  } catch {}
}

function commandSucceeds(command, args) {
  try {
    const res = spawnSync(command, args, { stdio: 'ignore' });
    return res.status === 0;
  } catch {
    return false;
  }
}

function detectLinuxPackageType() {
  const execPathLower = process.execPath.toLowerCase();
  if (process.env.APPIMAGE || execPathLower.endsWith('.appimage')) return 'appimage';
  if (commandSucceeds('pacman', ['-Qo', process.execPath])) return 'pacman';
  if (commandSucceeds('dpkg-query', ['-S', process.execPath])) return 'deb';
  if (commandSucceeds('dpkg', ['-S', process.execPath])) return 'deb';
  if (!app.isPackaged) return 'appimage';
  if (fs.existsSync('/etc/arch-release')) return 'pacman';
  if (fs.existsSync('/etc/debian_version') || fs.existsSync('/etc/lsb-release')) return 'deb';
  return 'appimage';
}

function getUpdateTarget() {
  if (process.platform === 'win32') {
    return {
      platform: 'win32',
      packageType: 'nsis',
      label: 'Windows',
      fileLabel: 'установщик Windows',
      matchers: [name => /\.exe$/i.test(name) && !/\.blockmap$/i.test(name)],
    };
  }

  if (process.platform === 'linux') {
    const packageType = detectLinuxPackageType();
    if (packageType === 'appimage') {
      return {
        platform: 'linux',
        packageType,
        label: 'Linux AppImage',
        fileLabel: 'AppImage',
        matchers: [/\.AppImage$/i],
      };
    }
    if (packageType === 'pacman') {
      return {
        platform: 'linux',
        packageType,
        label: 'Linux pacman',
        fileLabel: 'пакет pacman',
        matchers: [/\.(pacman|pkg\.tar\.xz|pkg\.tar\.zst)$/i],
      };
    }
    return {
      platform: 'linux',
      packageType: 'deb',
      label: 'Linux .deb',
      fileLabel: 'пакет .deb',
      matchers: [/\.deb$/i],
    };
  }

  return null;
}

function assetMatches(assetName, matchers) {
  return matchers.some((matcher) => {
    if (typeof matcher === 'function') return matcher(assetName);
    return matcher.test(assetName);
  });
}

function selectReleaseAsset(assets, target) {
  if (!Array.isArray(assets) || !target) return null;
  return assets.find(asset => asset?.name && assetMatches(asset.name, target.matchers)) || null;
}

function sanitizeFileName(name) {
  return String(name).replace(/[<>:"/\\|?*\x00-\x1f]/g, '-');
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, `'\"'\"'`)}'`;
}

function writeHelperScript(name, contents) {
  const dir = path.join(app.getPath('userData'), 'updates', 'scripts');
  fs.mkdirSync(dir, { recursive: true });
  const scriptPath = path.join(dir, name);
  fs.writeFileSync(scriptPath, contents, 'utf8');
  try { fs.chmodSync(scriptPath, 0o700); } catch {}
  return scriptPath;
}

function launchDetached(command, args, options = {}) {
  const child = spawn(command, args, {
    detached: true,
    stdio: 'ignore',
    ...options,
  });
  child.unref();
}

function getUpdateDownloadPath(info) {
  const dir = path.join(app.getPath('downloads'), 'FMclient Updates', info.version);
  fs.mkdirSync(dir, { recursive: true });
  return path.join(dir, sanitizeFileName(info.assetName));
}

function buildUpdateInfo(release, target, asset) {
  return {
    version: release.tag_name.replace(/^v/, ''),
    notes: release.body ? release.body.slice(0, 400) : '',
    releaseUrl: release.html_url,
    assetName: asset.name,
    assetUrl: asset.browser_download_url,
    assetSize: asset.size || 0,
    platform: target.platform,
    packageType: target.packageType,
    targetLabel: target.label,
    fileLabel: target.fileLabel,
  };
}

async function checkForUpdates() {
  if (!app.isPackaged) return null;
  if (!['win32', 'linux'].includes(process.platform)) return null;
  try {
    const release = await httpRequest(
      'GET',
      `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`,
      { 'User-Agent': FMCLIENT_UA, 'Accept': 'application/vnd.github+json' },
      null
    );

    if (!release?.tag_name) {
      updateState.info = null;
      updateState.downloadedPath = null;
      return null;
    }

    const latest = release.tag_name.replace(/^v/, '');
    if (!semverGt(latest, CURRENT_VER)) {
      updateState.info = null;
      updateState.downloadedPath = null;
      return null;
    }

    const target = getUpdateTarget();
    const asset = selectReleaseAsset(release.assets, target);
    if (!asset) {
      updateState.info = null;
      updateState.downloadedPath = null;
      return null;
    }

    const info = buildUpdateInfo(release, target, asset);
    updateState.info = info;
    updateState.downloadedPath = null;
    return info;
  } catch {
    updateState.info = null;
    updateState.downloadedPath = null;
    return null;
  }
}

async function downloadUpdatePackage(info) {
  const downloadPath = getUpdateDownloadPath(info);

  if (info.assetSize > 0 && fileOk(downloadPath, info.assetSize)) {
    return downloadPath;
  }

  await downloadFile(info.assetUrl, downloadPath, (done, total) => {
    const knownTotal = total || info.assetSize || 0;
    const progress = knownTotal > 0 ? Math.min(99, Math.max(1, Math.round(done / knownTotal * 100))) : null;
    sendUpdateStatus({
      state: 'downloading',
      message: progress !== null ? `Скачивание обновления: ${progress}%` : 'Скачивание обновления...',
      progress,
    });
  });

  if (info.assetSize > 0 && !fileOk(downloadPath, info.assetSize)) {
    throw new Error('Файл обновления скачался не полностью');
  }

  return downloadPath;
}

function installWindowsUpdate(installerPath) {
  const escapedPath = installerPath.replace(/"/g, '""');
  const scriptPath = writeHelperScript(
    `install-update-${Date.now()}.cmd`,
    [
      '@echo off',
      'timeout /t 2 /nobreak >nul',
      `start "" "${escapedPath}"`,
      'del "%~f0"',
    ].join('\r\n')
  );
  launchDetached('cmd.exe', ['/c', scriptPath], { windowsHide: true });
}

function installAppImageUpdate(downloadPath) {
  const appImagePath = process.env.APPIMAGE || (process.execPath.toLowerCase().endsWith('.appimage') ? process.execPath : null);
  if (!appImagePath) throw new Error('Текущий запуск не является AppImage');

  fs.accessSync(path.dirname(appImagePath), fs.constants.W_OK);

  const scriptPath = writeHelperScript(
    `install-update-${Date.now()}.sh`,
    `#!/bin/sh
set -e
sleep 1
SRC=${shellQuote(downloadPath)}
DST=${shellQuote(appImagePath)}
TMP="$DST.tmp"
chmod +x "$SRC"
cp "$SRC" "$TMP"
chmod +x "$TMP"
mv -f "$TMP" "$DST"
"$DST" >/dev/null 2>&1 &
rm -f "$SRC"
rm -f "$0"
`
  );

  launchDetached('sh', [scriptPath]);
}

function installLinuxPackageUpdate(downloadPath, packageType) {
  const installCommand = packageType === 'pacman'
    ? `pacman -U --noconfirm ${shellQuote(downloadPath)}`
    : `apt install -y ${shellQuote(downloadPath)}`;

  const relaunchPath = process.execPath;
  const scriptPath = writeHelperScript(
    `install-update-${Date.now()}.sh`,
    `#!/bin/sh
sleep 1
if command -v pkexec >/dev/null 2>&1; then
  pkexec sh -c ${shellQuote(installCommand)}
  status=$?
  if [ "$status" -eq 0 ]; then
    ${shellQuote(relaunchPath)} >/dev/null 2>&1 &
  fi
else
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open ${shellQuote(downloadPath)} >/dev/null 2>&1 &
  fi
fi
rm -f "$0"
`
  );

  launchDetached('sh', [scriptPath]);
}

function installUpdatePackage(downloadPath, info) {
  if (process.platform === 'win32') {
    installWindowsUpdate(downloadPath);
    return;
  }

  if (info.packageType === 'appimage') {
    installAppImageUpdate(downloadPath);
    return;
  }

  installLinuxPackageUpdate(downloadPath, info.packageType);
}

async function startUpdate() {
  const info = updateState.info || await checkForUpdates();
  if (!info) return { success: false, message: 'Для текущей установки обновление не найдено' };
  if (updateState.downloading || updateState.installing) {
    return { success: false, message: 'Обновление уже выполняется' };
  }

  try {
    updateState.downloading = true;
    sendUpdateStatus({ state: 'downloading', message: 'Подготовка обновления...', progress: 0 });
    const downloadPath = await downloadUpdatePackage(info);

    updateState.downloading = false;
    updateState.installing = true;
    updateState.downloadedPath = downloadPath;

    const installMessage = info.packageType === 'nsis'
      ? 'Запускаю установщик обновления...'
      : info.packageType === 'appimage'
        ? 'Подготавливаю замену AppImage...'
        : 'Запускаю установку обновления...';

    sendUpdateStatus({ state: 'installing', message: installMessage, progress: 100 });
    installUpdatePackage(downloadPath, info);
    setTimeout(() => app.quit(), 350);
    return { success: true };
  } catch (e) {
    updateState.downloading = false;
    updateState.installing = false;
    sendUpdateStatus({ state: 'error', message: e.message });
    return { success: false, message: e.message };
  }
}

ipcMain.handle('check-update', async () => checkForUpdates());
ipcMain.handle('start-update', async () => startUpdate());
ipcMain.handle('open-release', async (_, url) => { shell.openExternal(url); });

// ── HTTP helper (JSON only) ───────────────────────────────────
function httpRequest(method, url, headers, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const payload = body ? (typeof body === 'string' ? body : JSON.stringify(body)) : null;
    const options = {
      hostname: u.hostname,
      path: u.pathname + u.search,
      method,
      headers: { ...headers, ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}) },
    };
    const mod = u.protocol === 'https:' ? https : http;
    const req = mod.request(options, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch { resolve(data); }
      });
    });
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

// ── Download file with redirect support + progress + timeout ──
function downloadFile(url, dest, onProgress) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    const tmp = dest + '.dl';

    function fetch(url, hops) {
      if (hops > 10) return reject(new Error('Too many redirects'));
      const u   = new URL(url);
      const mod = u.protocol === 'https:' ? https : http;
      const req = mod.get(url, res => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          fetch(res.headers.location, hops + 1);
          return;
        }
        if (res.statusCode !== 200) {
          res.resume();
          reject(new Error(`HTTP ${res.statusCode} downloading ${url}`));
          return;
        }
        const total = parseInt(res.headers['content-length'] || '0');
        let received = 0;
        const out = fs.createWriteStream(tmp);
        res.on('data', chunk => {
          received += chunk.length;
          if (onProgress && total > 0) onProgress(received, total);
        });
        res.on('error', err => { out.destroy(); reject(err); });
        res.pipe(out);
        out.on('finish', () => out.close(() => fs.rename(tmp, dest, e => e ? reject(e) : resolve())));
        out.on('error', reject);
      });
      // 30-second timeout per file
      req.setTimeout(30000, () => req.destroy(new Error(`Timeout: ${url}`)));
      req.on('error', reject);
    }

    fetch(url, 0);
  });
}

// ── Parallel downloader ───────────────────────────────────────
async function downloadParallel(tasks, concurrency, onProgress) {
  let done = 0;
  const total = tasks.length;
  const queue = [...tasks];
  async function worker() {
    while (queue.length > 0) {
      const task = queue.shift();
      if (!task) break;
      await task();
      done++;
      if (onProgress) onProgress(done, total);
    }
  }
  const workers = Array.from({ length: Math.min(concurrency, tasks.length || 1) }, worker);
  await Promise.all(workers);
}

// ── File size check ───────────────────────────────────────────
function fileOk(p, size) {
  try { return fs.statSync(p).size === size; } catch { return false; }
}

// ── Minimal ZIP extractor (natives) ──────────────────────────
function extractNativesFromJar(jarPath, nativesDir) {
  try {
    const buf = fs.readFileSync(jarPath);
    // Find End of Central Directory record
    let eocd = -1;
    for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65558); i--) {
      if (buf[i]===0x50 && buf[i+1]===0x4b && buf[i+2]===0x05 && buf[i+3]===0x06) { eocd = i; break; }
    }
    if (eocd === -1) return;

    const cdOffset = buf.readUInt32LE(eocd + 16);
    const cdCount  = buf.readUInt16LE(eocd + 10);
    const nativeExts = new Set(['.so', '.dylib', '.jnilib', '.dll']);

    let pos = cdOffset;
    for (let i = 0; i < cdCount; i++) {
      if (buf.readUInt32LE(pos) !== 0x02014b50) break;
      const compression      = buf.readUInt16LE(pos + 10);
      const compressedSize   = buf.readUInt32LE(pos + 20);
      const fileNameLen      = buf.readUInt16LE(pos + 28);
      const extraLen         = buf.readUInt16LE(pos + 30);
      const commentLen       = buf.readUInt16LE(pos + 32);
      const localHeaderOff   = buf.readUInt32LE(pos + 42);
      const fileName         = buf.toString('utf8', pos + 46, pos + 46 + fileNameLen);
      pos += 46 + fileNameLen + extraLen + commentLen;

      if (fileName.endsWith('/')) continue;
      const ext = path.extname(fileName).toLowerCase();
      if (!nativeExts.has(ext)) continue;

      // Read local header
      const lh = localHeaderOff;
      if (buf.readUInt32LE(lh) !== 0x04034b50) continue;
      const fnLen2  = buf.readUInt16LE(lh + 26);
      const exLen2  = buf.readUInt16LE(lh + 28);
      const dataStart = lh + 30 + fnLen2 + exLen2;
      const compressed = buf.slice(dataStart, dataStart + compressedSize);

      let data;
      if (compression === 0) data = compressed;
      else if (compression === 8) { try { data = zlib.inflateRawSync(compressed); } catch { continue; } }
      else continue;

      fs.mkdirSync(nativesDir, { recursive: true });
      fs.writeFileSync(path.join(nativesDir, path.basename(fileName)), data);
    }
  } catch { /* ignore extraction errors */ }
}

// ── Pure-JS PNG encoder (no BrowserWindow, works on all OS) ──
function buildLogoPNG() {
  const W = 512, H = 256;
  // CRC32 table
  const crcTable = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    crcTable[n] = c >>> 0;
  }
  function crc32(buf) {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  }
  function pngChunk(type, data) {
    const t = Buffer.from(type, 'ascii');
    const lenBuf = Buffer.alloc(4); lenBuf.writeUInt32BE(data.length);
    const crcBuf = Buffer.alloc(4); crcBuf.writeUInt32BE(crc32(Buffer.concat([t, data])));
    return Buffer.concat([lenBuf, t, data, crcBuf]);
  }

  // Build pixel data (RGB): dark bg + purple→pink diagonal gradient + radial glows
  const pixels = Buffer.alloc(W * H * 3);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const tx = x / (W - 1);
      const ty = y / (H - 1);
      // Base dark: #080710
      let r = 8, g = 7, b = 16;
      // Purple (#a855f7) → pink (#ec4899) horizontal gradient, strength ~0.4
      const strength = 0.38 + ty * 0.08;
      r = Math.min(255, r + Math.round((168 + (236 - 168) * tx) * strength));
      g = Math.min(255, g + Math.round((85  + (72  - 85)  * tx) * strength * 0.45));
      b = Math.min(255, b + Math.round((247 + (153 - 247) * tx) * strength));
      // Center radial glow (purple)
      const dx = (x - W * 0.35) / (W * 0.35);
      const dy = (y - H * 0.5)  / (H * 0.5);
      const gl = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy)) * 0.55;
      r = Math.min(255, r + Math.round(gl * 168));
      g = Math.min(255, g + Math.round(gl * 60));
      b = Math.min(255, b + Math.round(gl * 247));
      // Right radial glow (pink)
      const dx2 = (x - W * 0.75) / (W * 0.3);
      const dy2 = (y - H * 0.5)  / (H * 0.5);
      const gl2 = Math.max(0, 1 - Math.sqrt(dx2 * dx2 + dy2 * dy2)) * 0.4;
      r = Math.min(255, r + Math.round(gl2 * 236));
      g = Math.min(255, g + Math.round(gl2 * 72));
      b = Math.min(255, b + Math.round(gl2 * 153));
      // Horizontal accent line near bottom (y≈200/256)
      const lineY = 0.78;
      const lineDist = Math.abs(ty - lineY);
      if (lineDist < 0.018) {
        const lp = 1 - lineDist / 0.018;
        const ltx = Math.max(0, Math.min(1, (tx - 0.2) / 0.6)); // fade edges
        const fade = ltx < 0.5 ? ltx * 2 : (1 - ltx) * 2;
        r = Math.min(255, r + Math.round(lp * fade * 168));
        g = Math.min(255, g + Math.round(lp * fade * 50));
        b = Math.min(255, b + Math.round(lp * fade * 200));
      }
      const idx = (y * W + x) * 3;
      pixels[idx] = r; pixels[idx + 1] = g; pixels[idx + 2] = b;
    }
  }

  // Build IHDR
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; ihdr[9] = 2; // bit depth 8, color type RGB

  // Build raw scanlines (filter byte 0 + RGB row)
  const rowLen = W * 3;
  const raw = Buffer.alloc((rowLen + 1) * H);
  for (let y = 0; y < H; y++) {
    raw[y * (rowLen + 1)] = 0;
    pixels.copy(raw, y * (rowLen + 1) + 1, y * rowLen, (y + 1) * rowLen);
  }
  const idat = zlib.deflateSync(raw, { level: 6 });

  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', idat),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// Wrap as async for interface compatibility
async function generateLogoBuffer() {
  return buildLogoPNG();
}

// ── Process JVM/game args (handles rule-based args) ──────────
function processArgs(args, vars) {
  if (!Array.isArray(args)) {
    // Old format: space-separated string
    return args.split(' ').map(a => a.replace(/\$\{([^}]+)\}/g, (_, k) => vars[k] ?? ''));
  }

  const curOS = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';

  // Features map — is_demo_user MUST be false, otherwise --demo gets passed
  const FEATURES = {
    is_demo_user:               false,
    has_custom_resolution:      false,
    is_quick_play_singleplayer: false,
    is_quick_play_multiplayer:  false,
    is_quick_play_realms:       false,
  };

  function ruleAllow(rule) {
    if (rule.features) {
      // Allow only if every required feature matches our map
      return Object.entries(rule.features).every(([k, v]) => FEATURES[k] === v);
    }
    if (rule.os) {
      if (rule.os.name && rule.os.name !== curOS) return false;
      return true;
    }
    return true; // no restrictions → allow
  }

  const result = [];
  for (const arg of args) {
    if (typeof arg === 'string') {
      result.push(arg.replace(/\$\{([^}]+)\}/g, (_, k) => vars[k] ?? ''));
    } else if (arg.rules) {
      let allow = false;
      for (const rule of arg.rules) {
        if (rule.action === 'allow'    &&  ruleAllow(rule)) allow = true;
        if (rule.action === 'disallow' &&  ruleAllow(rule)) allow = false;
      }
      if (allow) {
        const vals = Array.isArray(arg.value) ? arg.value : [arg.value];
        for (const v of vals) {
          result.push(v.replace(/\$\{([^}]+)\}/g, (_, k) => vars[k] ?? ''));
        }
      }
    }
  }
  return result;
}

// ── Offline UUID (Java convention) ────────────────────────────
function offlineUUID(username) {
  const hash = crypto.createHash('md5').update('OfflinePlayer:' + username).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const h = hash.toString('hex');
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20,32)}`;
}

// ── Local mock auth server (offline multiplayer without ely.by) ──
let mockAuthServer = null;
let mockAuthPort   = null;
const mockProfiles = new Map(); // uuidClean → name (module-level, accessible from launch handler)

function startMockAuthServer() {
  return new Promise((resolve) => {
    // Generate RSA key pair once — used for ALI metadata signature field
    const { publicKey } = crypto.generateKeyPairSync('rsa', {
      modulusLength: 2048,
      publicKeyEncoding:  { type: 'spki',  format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    });

    // Store pending join sessions for hasJoined lookup
    const pendingJoins = new Map(); // serverId → { name, uuid }

    const server = http.createServer((req, res) => {
      const u        = new URL(req.url, 'http://localhost');
      const pathname = u.pathname;

      const sendJSON = (data, status = 200) => {
        const body = JSON.stringify(data);
        res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
        res.end(body);
      };
      const sendOK = () => { res.writeHead(204); res.end(); };
      const readBody = () => new Promise(ok => {
        let d = '';
        req.on('data', c => d += c);
        req.on('end', () => { try { ok(JSON.parse(d)); } catch { ok({}); } });
      });

      // ── ALI metadata (authlib-injector fetches this at startup) ──
      if (pathname === '/' && req.method === 'GET') {
        return sendJSON({
          meta: {
            serverName: 'FMclient',
            implementationName: 'FMclient Auth',
            implementationVersion: '1.0.0',
            links: {},
          },
          skinDomains: ['*'],
          signaturePublickey: publicKey,
        });
      }

      // ── Authenticate ──────────────────────────────────────────
      if (pathname === '/authserver/authenticate' && req.method === 'POST') {
        return readBody().then(data => {
          const name     = (data.username || 'Player').replace(/\s+/g, '_').slice(0, 16);
          const uuid     = offlineUUID(name);
          const uuidClean = uuid.replace(/-/g, '');
          const token    = crypto.randomBytes(16).toString('hex');
          mockProfiles.set(uuidClean, name);
          sendJSON({
            accessToken:      token,
            clientToken:      data.clientToken || crypto.randomBytes(16).toString('hex'),
            selectedProfile:  { id: uuidClean, name },
            availableProfiles:[{ id: uuidClean, name }],
          });
        });
      }

      // ── Refresh ───────────────────────────────────────────────
      if (pathname === '/authserver/refresh' && req.method === 'POST') {
        return readBody().then(data => {
          const name     = data.selectedProfile?.name || mockProfiles.get(data.selectedProfile?.id) || 'Player';
          const uuid     = offlineUUID(name).replace(/-/g, '');
          mockProfiles.set(uuid, name);
          sendJSON({
            accessToken:     crypto.randomBytes(16).toString('hex'),
            clientToken:     data.clientToken || crypto.randomBytes(16).toString('hex'),
            selectedProfile: { id: uuid, name },
          });
        });
      }

      // ── Validate / invalidate / signout → always OK ───────────
      if (['/authserver/validate', '/authserver/invalidate', '/authserver/signout'].includes(pathname)) {
        return sendOK();
      }

      // ── Profile by UUID ───────────────────────────────────────
      if (pathname.startsWith('/sessionserver/session/minecraft/profile/') && req.method === 'GET') {
        const uuidClean = pathname.split('/').pop().replace(/-/g, '');
        const name      = mockProfiles.get(uuidClean) || 'Player';
        return sendJSON({ id: uuidClean, name, properties: [] });
      }

      // ── Client → Join session (before entering server) ────────
      if (pathname === '/sessionserver/session/minecraft/join' && req.method === 'POST') {
        return readBody().then(data => {
          const name = data.selectedProfile?.name || mockProfiles.get(data.selectedProfile?.id?.replace(/-/g,'')) || 'Player';
          const uuid = (data.selectedProfile?.id || '').replace(/-/g, '');
          if (data.serverId) pendingJoins.set(data.serverId, { name, uuid });
          sendOK();
        });
      }

      // ── Server → hasJoined (server verifies player joined) ────
      if (pathname === '/sessionserver/session/minecraft/hasJoined' && req.method === 'GET') {
        const username = u.searchParams.get('username') || 'Player';
        const serverId = u.searchParams.get('serverId') || '';
        const pending  = pendingJoins.get(serverId);
        const name     = pending?.name || username;
        const uuid     = pending?.uuid || offlineUUID(name).replace(/-/g, '');
        if (serverId) pendingJoins.delete(serverId);
        mockProfiles.set(uuid, name);
        return sendJSON({ id: uuid, name, properties: [] });
      }

      // ── Bulk profile lookup by names ──────────────────────────
      if (pathname === '/api/profiles/minecraft' && req.method === 'POST') {
        return readBody().then(names => {
          const profiles = (Array.isArray(names) ? names : []).map(n => ({
            id:   offlineUUID(n).replace(/-/g, ''),
            name: n,
          }));
          sendJSON(profiles);
        });
      }

      res.writeHead(404); res.end();
    });

    server.listen(0, '127.0.0.1', () => {
      mockAuthPort   = server.address().port;
      mockAuthServer = server;
      resolve(mockAuthPort);
    });
  });
}

// ── servers.dat NBT encoder/decoder ──────────────────────────
function buildServersDat(servers) {
  function nbtStr(s) {
    const b = Buffer.from(s, 'utf8');
    const h = Buffer.alloc(2);
    h.writeUInt16BE(b.length);
    return Buffer.concat([h, b]);
  }
  function namedTag(type, name, payload) {
    return Buffer.concat([Buffer.from([type]), nbtStr(name), payload]);
  }
  const serverBufs = servers.map(s => Buffer.concat([
    namedTag(8, 'ip',   nbtStr(s.ip)),
    namedTag(8, 'name', nbtStr(s.name)),
    Buffer.from([0]), // TAG_End
  ]));
  const countBuf = Buffer.alloc(4);
  countBuf.writeInt32BE(servers.length);
  const listPayload = Buffer.concat([Buffer.from([10]), countBuf, ...serverBufs]);
  return Buffer.concat([
    Buffer.from([10]),          // root TAG_Compound
    nbtStr(''),                  // root name (empty)
    namedTag(9, 'servers', listPayload),
    Buffer.from([0]),            // TAG_End
  ]);
}

function parseServersDat(buf) {
  let pos = 0;
  const readU8  = ()  => buf[pos++];
  const readU16 = ()  => { const v = buf.readUInt16BE(pos); pos += 2; return v; };
  const readI32 = ()  => { const v = buf.readInt32BE(pos);  pos += 4; return v; };
  const readStr = ()  => { const n = readU16(); const s = buf.toString('utf8', pos, pos + n); pos += n; return s; };

  function skipTag(type) {
    if (type === 1)  { pos += 1; }
    else if (type === 2) { pos += 2; }
    else if (type === 3) { pos += 4; }
    else if (type === 4) { pos += 8; }
    else if (type === 5) { pos += 4; }
    else if (type === 6) { pos += 8; }
    else if (type === 7) { pos += readI32(); }
    else if (type === 8) { pos += readU16(); }
    else if (type === 9) { const et = readU8(); const n = readI32(); for (let i=0;i<n;i++) skipTag(et); }
    else if (type === 10) { let t; while ((t = readU8()) !== 0) { readStr(); skipTag(t); } }
    else if (type === 11) { pos += readI32() * 4; }
    else if (type === 12) { pos += readI32() * 8; }
  }

  try {
    if (readU8() !== 10) return [];
    readStr(); // root name
    const servers = [];
    let t;
    while ((t = readU8()) !== 0) {
      const name = readStr();
      if (t === 9 && name === 'servers') {
        readU8(); // element type (compound)
        const count = readI32();
        for (let i = 0; i < count; i++) {
          const srv = {};
          let ft;
          while ((ft = readU8()) !== 0) {
            const fn = readStr();
            if (ft === 8) { srv[fn] = readStr(); } else { skipTag(ft); }
          }
          servers.push(srv);
        }
      } else { skipTag(t); }
    }
    return servers;
  } catch { return []; }
}

function patchServersDat(gameDir) {
  const FIREMINE_IP   = 'mc.firemine.su';
  const FIREMINE_NAME = 'Лучший сервер';
  const filePath = path.join(gameDir, 'servers.dat');

  let existing = [];
  try { existing = parseServersDat(fs.readFileSync(filePath)); } catch {}

  if (existing.some(s => s.ip === FIREMINE_IP)) return; // already present

  const allServers = [{ ip: FIREMINE_IP, name: FIREMINE_NAME }, ...existing];
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, buildServersDat(allServers));
}

// ── Replace Mojang splash logo with FMclient logo ─────────────
async function replaceMojangLogo(assetObjects, objectsDir) {
  const logoKeys = [
    'minecraft/textures/gui/title/mojangstudios.png',
    'minecraft/textures/gui/title/mojang.png',
    'minecraft/textures/misc/mojangstudios.png',
  ];
  let logoBuf = null;
  for (const key of logoKeys) {
    const obj = assetObjects[key];
    if (!obj) continue;
    const hash   = obj.hash;
    const prefix = hash.substring(0, 2);
    const objPath = path.join(objectsDir, prefix, hash);
    if (fs.existsSync(objPath)) {
      try {
        if (!logoBuf) logoBuf = await generateLogoBuffer();
        fs.writeFileSync(objPath, logoBuf);
      } catch { /* ignore */ }
    }
  }
}

// ── Window setup ──────────────────────────────────────────────
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100, height: 680,
    minWidth: 1060, minHeight: 630,
    frame: false, transparent: true, backgroundColor: '#00000000',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    show: false, center: true, resizable: true,
  });
  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    // Check for updates after window is shown (non-blocking)
    setTimeout(async () => {
      const update = await checkForUpdates();
      if (update && mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('update-available', update);
      }
    }, 3000);
  });
}

app.whenReady().then(async () => {
  await startMockAuthServer();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

// ── IPC: Window ───────────────────────────────────────────────
ipcMain.on('window-minimize', () => mainWindow.minimize());
ipcMain.on('window-close',    () => mainWindow.close());
ipcMain.on('window-maximize', () => {
  if (mainWindow.isMaximized()) mainWindow.unmaximize(); else mainWindow.maximize();
});

// ── IPC: System info ──────────────────────────────────────────
ipcMain.handle('get-system-info', () => ({
  totalMemory: Math.floor(os.totalmem() / 1024 / 1024),
  platform: process.platform,
  arch: os.arch(),
  homedir: os.homedir(),
}));

// ── IPC: Java detection ───────────────────────────────────────
ipcMain.handle('detect-java', async () => {
  const candidates = [];
  if (process.platform === 'win32') {
    if (process.env.JAVA_HOME) candidates.push(path.join(process.env.JAVA_HOME, 'bin', 'java.exe'));
    const roots = [process.env['ProgramFiles'], process.env['ProgramFiles(x86)'],
      process.env['ProgramW6432'], 'C:\\Program Files', 'C:\\Program Files (x86)'].filter(Boolean);
    for (const root of roots)
      for (const vendor of ['Java','Eclipse Adoptium','Microsoft','BellSoft','Amazon Corretto','Zulu','OpenJDK']) {
        const dir = path.join(root, vendor);
        if (fs.existsSync(dir)) {
          try { for (const e of fs.readdirSync(dir)) {
            const p = path.join(dir, e, 'bin', 'java.exe');
            if (fs.existsSync(p)) candidates.push(p);
          } } catch {}
        }
      }
    candidates.push('java');
  } else {
    if (process.env.JAVA_HOME) candidates.push(path.join(process.env.JAVA_HOME, 'bin', 'java'));
    for (const dir of ['/usr/lib/jvm','/usr/local/lib/jvm','/opt/java','/opt/jdk']) {
      if (fs.existsSync(dir)) {
        try { for (const e of fs.readdirSync(dir)) {
          const p = path.join(dir, e, 'bin', 'java');
          if (fs.existsSync(p)) candidates.push(p);
        } } catch {}
      }
    }
    candidates.push('/usr/bin/java', '/usr/local/bin/java', 'java');
  }
  for (const javaPath of candidates) {
    const result = await new Promise((resolve) => {
      exec(`"${javaPath}" -version`, (error, stdout, stderr) => {
        if (error) { resolve(null); return; }
        const output = stderr || stdout;
        const match  = output.match(/version "([^"]+)"/);
        const verStr = match ? match[1] : 'unknown';
        const major  = verStr.startsWith('1.') ? parseInt(verStr.split('.')[1]) : parseInt(verStr.split('.')[0]);
        resolve({ found: true, version: verStr, major, path: javaPath });
      });
    });
    if (result) { detectedJavaPath = result.path; return result; }
  }
  return { found: false };
});

// ── IPC: Microsoft Auth ───────────────────────────────────────
const MS_CLIENT_ID = '00000000402b5328';
const MS_REDIRECT  = 'https://login.live.com/oauth20_desktop.srf';
const MS_AUTH_URL  = `https://login.live.com/oauth20_authorize.srf?client_id=${MS_CLIENT_ID}&response_type=code&scope=XboxLive.signin%20offline_access&redirect_uri=${encodeURIComponent(MS_REDIRECT)}&prompt=select_account`;

async function openAuthWindow() {
  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      width: 480, height: 680, title: 'Войти через Microsoft',
      parent: mainWindow, modal: true,
      webPreferences: { nodeIntegration: false, contextIsolation: true },
      autoHideMenuBar: true,
    });
    win.loadURL(MS_AUTH_URL);
    const checkUrl = (url) => {
      if (url.startsWith(MS_REDIRECT)) {
        const code  = new URL(url).searchParams.get('code');
        const error = new URL(url).searchParams.get('error');
        win.close();
        if (code) resolve(code); else reject(new Error(error || 'Авторизация отменена'));
      }
    };
    win.webContents.on('will-redirect', (e, url) => checkUrl(url));
    win.webContents.on('did-navigate',  (e, url) => checkUrl(url));
    win.on('closed', () => reject(new Error('Окно закрыто')));
  });
}

async function exchangeCodeForTokens(code) {
  return httpRequest('POST', 'https://login.live.com/oauth20_token.srf',
    { 'Content-Type': 'application/x-www-form-urlencoded' },
    new URLSearchParams({ client_id: MS_CLIENT_ID, code, grant_type: 'authorization_code', redirect_uri: MS_REDIRECT }).toString());
}
async function refreshMSToken(refreshToken) {
  return httpRequest('POST', 'https://login.live.com/oauth20_token.srf',
    { 'Content-Type': 'application/x-www-form-urlencoded' },
    new URLSearchParams({ client_id: MS_CLIENT_ID, refresh_token: refreshToken, grant_type: 'refresh_token' }).toString());
}
async function getXBLToken(msAccessToken) {
  return httpRequest('POST', 'https://user.auth.xboxlive.com/user/authenticate',
    { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    { Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${msAccessToken}` }, RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT' });
}
async function getXSTSToken(xblToken) {
  return httpRequest('POST', 'https://xsts.auth.xboxlive.com/xsts/authorize',
    { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    { Properties: { SandboxId: 'RETAIL', UserTokens: [xblToken] }, RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT' });
}
async function getMCToken(xstsToken, userHash) {
  return httpRequest('POST', 'https://api.minecraftservices.com/authentication/login_with_xbox',
    { 'Content-Type': 'application/json' },
    { identityToken: `XBL3.0 x=${userHash};${xstsToken}` });
}
async function getMCProfile(mcAccessToken) {
  return httpRequest('GET', 'https://api.minecraftservices.com/minecraft/profile',
    { Authorization: `Bearer ${mcAccessToken}` }, null);
}
async function fullAuthFlow(msAccessToken, refreshToken, expiresIn) {
  const xbl  = await getXBLToken(msAccessToken);
  const uhs  = xbl.DisplayClaims.xui[0].uhs;
  const xsts = await getXSTSToken(xbl.Token);
  if (xsts.XErr) {
    const errs = {
      2148916233: 'У аккаунта нет профиля Xbox. Зайди на xbox.com и создай его.',
      2148916238: 'Аккаунт несовершеннолетнего. Нужно добавить в семейную группу.',
    };
    throw new Error(errs[xsts.XErr] || `Ошибка Xbox: ${xsts.XErr}`);
  }
  const mc = await getMCToken(xsts.Token, uhs);
  if (!mc.access_token) throw new Error('Не удалось получить токен Minecraft');
  const profile = await getMCProfile(mc.access_token);
  if (!profile?.name) throw new Error('Minecraft не куплен на этом аккаунте');
  return { type: 'msa', username: profile.name, uuid: profile.id, accessToken: mc.access_token,
    refreshToken, expiresAt: Date.now() + (expiresIn * 1000) };
}

ipcMain.handle('auth-microsoft', async () => {
  try {
    const code     = await openAuthWindow();
    const tokens   = await exchangeCodeForTokens(code);
    if (!tokens.access_token) throw new Error('Не удалось получить токен Microsoft');
    const authData = await fullAuthFlow(tokens.access_token, tokens.refresh_token, tokens.expires_in || 3600);
    saveAuth(authData);
    return { success: true, username: authData.username, uuid: authData.uuid };
  } catch (e) { return { success: false, error: e.message }; }
});
ipcMain.handle('auth-refresh', async () => {
  const auth = loadAuth();
  if (!auth?.refreshToken) return { success: false };
  try {
    if (auth.expiresAt && Date.now() < auth.expiresAt - 300_000) return { success: true, username: auth.username, uuid: auth.uuid };
    const tokens = await refreshMSToken(auth.refreshToken);
    if (!tokens.access_token) return { success: false };
    const authData = await fullAuthFlow(tokens.access_token, tokens.refresh_token || auth.refreshToken, tokens.expires_in || 3600);
    saveAuth(authData);
    return { success: true, username: authData.username, uuid: authData.uuid };
  } catch { return { success: false }; }
});
ipcMain.handle('auth-get-stored', () => { const a = loadAuth(); return a ? { username: a.username, uuid: a.uuid, type: a.type || 'msa' } : null; });
ipcMain.handle('auth-logout', () => { clearAuth(); return { success: true }; });

// ── IPC: ely.by Auth ──────────────────────────────────────────
ipcMain.handle('auth-elyby', async (event, { username, password }) => {
  try {
    const clientToken = crypto.randomBytes(16).toString('hex');
    const resp = await httpRequest('POST',
      'https://authserver.ely.by/auth/authenticate',
      { 'Content-Type': 'application/json' },
      {
        username,
        password,
        clientToken,
        agent: { name: 'Minecraft', version: 1 },
        requestUser: true,
      }
    );
    if (!resp.accessToken) throw new Error(resp.errorMessage || resp.error || 'Неверный логин или пароль');
    const authData = {
      type:        'elyby',
      username:    resp.selectedProfile.name,
      uuid:        resp.selectedProfile.id,
      accessToken: resp.accessToken,
      clientToken,
    };
    saveAuth(authData);
    return { success: true, username: authData.username, uuid: authData.uuid };
  } catch (e) { return { success: false, error: e.message }; }
});

// ── Download authlib-injector (needed for ely.by multiplayer) ─
// ── Ensure FMclient Visuals mod is always present in mods/ ───────
// The mod is bundled with the launcher in assets/mods/fmvisuals.jar
// It is restored automatically if the user deletes it.
function sendDebug(msg) {
  try {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('debug-log', `[FMclient] ${msg}`);
    }
  } catch {}
}

function ensureFMVisuals(gameDir) {
  try {
    const modsDir = path.join(gameDir, 'mods');
    fs.mkdirSync(modsDir, { recursive: true });
    const dest = path.join(modsDir, 'fmvisuals.jar');
    const src  = app.isPackaged
      ? path.join(process.resourcesPath, 'assets', 'mods', 'fmvisuals.jar')
      : path.join(__dirname, 'assets', 'mods', 'fmvisuals.jar');

    sendDebug(`ensureFMVisuals: gameDir=${gameDir}`);
    sendDebug(`ensureFMVisuals: src=${src} exists=${fs.existsSync(src)}`);
    sendDebug(`ensureFMVisuals: dest=${dest}`);

    if (!fs.existsSync(src)) {
      sendDebug(`ensureFMVisuals: ERROR — source JAR not found!`);
      return;
    }
    fs.copyFileSync(src, dest);
    sendDebug(`ensureFMVisuals: copied OK (${fs.statSync(dest).size} bytes)`);
  } catch (e) {
    sendDebug(`ensureFMVisuals: EXCEPTION — ${e.message}`);
  }
}

function ensureRussianLanguage(gameDir) {
  try {
    const optionsPath = path.join(gameDir, 'options.txt');
    fs.mkdirSync(gameDir, { recursive: true });

    if (!fs.existsSync(optionsPath)) {
      fs.writeFileSync(optionsPath, 'lang:ru_ru\n', 'utf8');
      return;
    }

    const raw = fs.readFileSync(optionsPath, 'utf8');
    if (/^lang:/m.test(raw)) {
      const next = raw.replace(/^lang:.*/m, 'lang:ru_ru');
      if (next !== raw) fs.writeFileSync(optionsPath, next, 'utf8');
    } else {
      const suffix = raw.endsWith('\n') || raw.length === 0 ? '' : '\n';
      fs.writeFileSync(optionsPath, raw + suffix + 'lang:ru_ru\n', 'utf8');
    }
  } catch (e) {
    sendDebug(`ensureRussianLanguage: EXCEPTION - ${e.message}`);
  }
}

async function ensureAuthlibInjector(gameDir) {
  const jar = path.join(gameDir, 'authlib-injector.jar');
  if (fs.existsSync(jar) && fs.statSync(jar).size > 100_000) return jar;
  const url = 'https://github.com/yushijinhun/authlib-injector/releases/download/v1.2.5/authlib-injector-1.2.5.jar';
  await downloadFile(url, jar, null);
  return jar;
}

// ── IPC: Check / Delete version ───────────────────────────────
ipcMain.handle('check-version', (event, { version, gameDir }) => {
  const dir = resolveGameDir(gameDir);
  const jar = path.join(dir, 'versions', version, `${version}.jar`);
  return fs.existsSync(jar);
});

ipcMain.handle('delete-version', (event, { version, gameDir }) => {
  try {
    const dir = resolveGameDir(gameDir);
    const versionDir = path.join(dir, 'versions', version);
    if (!fs.existsSync(versionDir)) return { success: false, message: 'Версия не найдена' };
    fs.rmSync(versionDir, { recursive: true, force: true });
    return { success: true };
  } catch (e) {
    return { success: false, message: e.message };
  }
});

// ── IPC: Get Versions ─────────────────────────────────────────
ipcMain.handle('get-versions', async () => {
  try {
    const manifest = await httpRequest('GET',
      'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', {}, null);
    const releases = manifest.versions
      .filter(v => v.type === 'release')
      .slice(0, 20)
      .map(v => ({ id: v.id, type: v.type }));
    return { success: true, versions: releases };
  } catch (e) {
    return { success: false, error: e.message };
  }
});

// ── IPC: Launch game ──────────────────────────────────────────
ipcMain.handle('launch-game', async (event, options) => {
  if (gameProcess) return { success: false, message: 'Игра уже запущена!' };

  const { version, type, username, ram, gameDir: gameDirOpt, javaPath: javaPathOpt } = options;

  const gameDir  = resolveGameDir(gameDirOpt);
  const javaExec = javaPathOpt?.trim() || detectedJavaPath || 'java';

  const send = (step, current, total, message) => {
    if (mainWindow && !mainWindow.isDestroyed())
      mainWindow.webContents.send('launch-progress', { step, current, total, message });
  };

  sendDebug(`=== LAUNCH START ===`);
  sendDebug(`version=${version} type=${type} gameDir=${gameDir} java=${javaExec}`);

  try {
    // Auth data
    const auth        = loadAuth();
    const accessToken = auth?.accessToken || crypto.randomBytes(16).toString('hex');
    const uuid        = auth?.uuid        || offlineUUID(username);
    // Always use 'msa' — 'legacy' triggers demo mode in MC 1.18+ because
    // Mojang sunset legacy accounts; 'msa' with any token works offline
    const userType    = 'msa';

    // ── 1. Version manifest ───────────────────────────────────
    send('manifest', 0, 1, 'Получение манифеста версий...');
    const manifest = await httpRequest('GET',
      'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', {}, null);
    const versionEntry = manifest.versions.find(v => v.id === version);
    if (!versionEntry) throw new Error(`Версия ${version} не найдена в реестре Mojang`);

    send('manifest', 1, 1, 'Загрузка данных версии...');
    const vJson = await httpRequest('GET', versionEntry.url, {}, null);

    // ── Merge modloader profile into vJson if needed ──────────
    if (type && type !== 'vanilla' && type !== 'optifine') {
      send('loader', 0, 100, `Проверка ${loaderLabel(type)}...`);
      const loaderStatus = await ensureLoaderInstalled({
        type,
        mcVersion: version,
        gameDir,
        javaExec,
        onProgress: (message, pct) => send('loader', pct ?? 0, 100, message),
      });
      const profileId = loaderStatus.versionId || findInstalledLoaderProfileId(type, version, gameDir);
      if (!profileId) throw new Error(`${loaderLabel(type)} не удалось подготовить для MC ${version}`);

      const profilePath = path.join(gameDir, 'versions', profileId, `${profileId}.json`);
      if (fs.existsSync(profilePath)) {
        const loaderJson = JSON.parse(fs.readFileSync(profilePath, 'utf8'));
        mergeLoaderProfileIntoVersionJson(vJson, loaderJson);
      }
    }

    // ── Dirs ──────────────────────────────────────────────────
    const versionsDir  = path.join(gameDir, 'versions', version);
    const librariesDir = path.join(gameDir, 'libraries');
    const assetsDir    = path.join(gameDir, 'assets');
    const nativesDir   = path.join(versionsDir, 'natives');
    [versionsDir, librariesDir, assetsDir, nativesDir].forEach(d => fs.mkdirSync(d, { recursive: true }));
    fs.writeFileSync(path.join(versionsDir, `${version}.json`), JSON.stringify(vJson, null, 2));

    // ── 2. Client JAR ─────────────────────────────────────────
    const clientJar = path.join(versionsDir, `${version}.jar`);
    const clientDl  = vJson.downloads?.client;
    if (clientDl && !fileOk(clientJar, clientDl.size)) {
      send('client', 0, 1, 'Скачивание клиента Minecraft...');
      await downloadFile(clientDl.url, clientJar, (done, total) =>
        send('client', done, total, `Клиент: ${Math.round(done / total * 100)}%`));
    }

    // ── 3. Libraries ──────────────────────────────────────────
    const curOS    = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';
    const libs     = vJson.libraries || [];
    const classPath = [clientJar];
    const libTasks  = [];

    for (const lib of libs) {
      // Rule check
      if (lib.rules) {
        let allowed = false;
        for (const rule of lib.rules) {
          if (rule.action === 'allow')    { if (!rule.os || rule.os.name === curOS) allowed = true; }
          if (rule.action === 'disallow') { if (!rule.os || rule.os.name === curOS) allowed = false; }
        }
        if (!allowed) continue;
      }

      const artifact = lib.downloads?.artifact;
      if (artifact) {
        const libPath = path.join(librariesDir, artifact.path);
        classPath.push(libPath);
        if (!fileOk(libPath, artifact.size)) {
          libTasks.push(() => downloadFile(artifact.url, libPath, null));
        }
      } else if (lib.name) {
        // Maven coordinate format (Fabric, NeoForge, Forge, etc.)
        const parts4 = lib.name.split(':');
        if (parts4.length >= 3) {
          const [grp, art4, ver4, ...rest4] = parts4;
          const classifier = rest4[0] || '';
          const fname      = classifier ? `${art4}-${ver4}-${classifier}.jar` : `${art4}-${ver4}.jar`;
          const libRelPath = `${grp.replace(/\./g, '/')}/${art4}/${ver4}/${fname}`;
          const libPath    = path.join(librariesDir, libRelPath);
          classPath.push(libPath);
          if (!fs.existsSync(libPath) && lib.url) {
            const dlUrl = `${lib.url.replace(/\/$/, '')}/${libRelPath}`;
            libTasks.push(() => downloadFile(dlUrl, libPath, null).catch(() => {}));
          }
        }
      }

      // Old-style natives (1.12.2, 1.8.9 etc.)
      const nativeKey = lib.natives?.[curOS];
      if (nativeKey && lib.downloads?.classifiers) {
        const nativeDl   = lib.downloads.classifiers[nativeKey];
        if (nativeDl) {
          const nativePath = path.join(librariesDir, nativeDl.path);
          if (!fileOk(nativePath, nativeDl.size)) {
            libTasks.push(() => downloadFile(nativeDl.url, nativePath, null)
              .then(() => extractNativesFromJar(nativePath, nativesDir)));
          } else {
            extractNativesFromJar(nativePath, nativesDir);
          }
        }
      }
    }

    if (libTasks.length > 0) {
      let libDone = 0;
      send('libraries', 0, libTasks.length, `Скачивание библиотек: 0/${libTasks.length}`);
      await downloadParallel(libTasks, 8, (done, total) => {
        libDone = done;
        send('libraries', done, total, `Библиотеки: ${done}/${total}`);
      });
    }

    // ── 4. Asset index ────────────────────────────────────────
    const assetIndexInfo = vJson.assetIndex;
    const assetIndexDir  = path.join(assetsDir, 'indexes');
    fs.mkdirSync(assetIndexDir, { recursive: true });
    const assetIndexPath = path.join(assetIndexDir, `${assetIndexInfo.id}.json`);

    if (!fileOk(assetIndexPath, assetIndexInfo.size)) {
      send('assets', 0, 1, 'Скачивание индекса ресурсов...');
      await downloadFile(assetIndexInfo.url, assetIndexPath, null);
    }

    const assetIndex   = JSON.parse(fs.readFileSync(assetIndexPath, 'utf8'));
    const assetObjects = assetIndex.objects;
    const objectsDir   = path.join(assetsDir, 'objects');
    const assetKeys    = Object.keys(assetObjects);

    // Build download tasks for missing assets (skip on error, don't hang)
    const assetTasks = [];
    for (const key of assetKeys) {
      const obj    = assetObjects[key];
      const hash   = obj.hash;
      const prefix = hash.substring(0, 2);
      const objDir = path.join(objectsDir, prefix);
      const objPath = path.join(objDir, hash);
      if (!fileOk(objPath, obj.size)) {
        assetTasks.push(() => downloadFile(
          `https://resources.download.minecraft.net/${prefix}/${hash}`, objPath, null
        ).catch(() => { /* skip failed asset, game usually still works */ }));
      }
    }

    if (assetTasks.length > 0) {
      send('assets', 0, assetTasks.length, `Ресурсы: 0/${assetTasks.length}`);
      await downloadParallel(assetTasks, 16, (done, total) =>
        send('assets', done, total, `Ресурсы: ${done}/${total}`));
    }

    // ── 5. Replace Mojang logo with FMclient logo ─────────────
    send('logo', 0, 1, 'Применение логотипа FMclient...');
    await replaceMojangLogo(assetObjects, objectsDir);
    send('logo', 1, 1, 'Логотип применён');

    // ── 6. Patch servers.dat ──────────────────────────────────
    try { patchServersDat(gameDir); } catch {}

    activateProfileMods(gameDir, version, type);
    // ── Ensure FMclient Visuals mod is present (restore if deleted) ──
    ensureFMVisuals(gameDir);
    ensureRussianLanguage(gameDir);

    // ── 7. Build launch command ───────────────────────────────
    send('launch', 0, 1, 'Запуск Minecraft...');
    const cpSep = process.platform === 'win32' ? ';' : ':';

    const vars = {
      auth_player_name:    username,
      version_name:        version,
      game_directory:      gameDir,
      assets_root:         assetsDir,
      assets_index_name:   assetIndexInfo.id,
      auth_uuid:           uuid,
      auth_access_token:   accessToken,
      // clientid + auth_xuid required in MC 1.18+ for MSA; any non-empty value prevents demo
      clientid:            '0',
      auth_xuid:           '0',
      user_type:           userType,
      version_type:        vJson.type || 'release',
      resolution_width:    '1280',
      resolution_height:   '720',
      natives_directory:   nativesDir,
      launcher_name:       'FMclient',
      launcher_version:    CURRENT_VER,
      classpath:           classPath.join(cpSep),
    };

    // Base JVM args
    const baseJvm = [
      `-Xmx${ram}m`,
      `-Xms512m`,
      `-Dminecraft.launcher.brand=FMclient`,
      `-Dminecraft.launcher.version=1.0.0`,
    ];

    // Inject authlib-injector only for non-MSA users:
    //   ely.by  → redirect to ely.by auth server (skins + multiplayer)
    //   offline → redirect to local mock server (cracked multiplayer)
    //   msa     → NO injection; the game talks directly to Mojang's real session servers
    //             so licensed servers and official skins work correctly
    send('logo', 0, 1, 'Подготовка мультиплеера...');
    // Inject only for ely.by or offline (no auth); MSA goes directly to Mojang
    if (auth?.type === 'elyby' || !auth) {
      try {
        const aiJar      = await ensureAuthlibInjector(gameDir);
        const authServer = auth?.type === 'elyby'
          ? 'https://authserver.ely.by/api/authlib-injector'
          : `http://127.0.0.1:${mockAuthPort}`;
        baseJvm.unshift(`-javaagent:${aiJar}=${authServer}`);

        // Populate mock profile so hasJoined can look up UUID by username
        if (auth?.type !== 'elyby') {
          const uuidClean = uuid.replace(/-/g, '');
          mockProfiles.set(uuidClean, username);
        }
      } catch { /* non-fatal */ }
    }
    send('logo', 1, 1, 'Мультиплеер готов');

    let jvmArgs, gameArgs;

    if (vJson.arguments?.jvm) {
      // Modern format (1.13+): JVM args from JSON include -cp and -Djava.library.path
      jvmArgs  = [...baseJvm, ...processArgs(vJson.arguments.jvm, vars)];
      gameArgs = processArgs(vJson.arguments.game, vars);
    } else {
      // Legacy format
      jvmArgs  = [...baseJvm, `-Djava.library.path=${nativesDir}`, '-cp', classPath.join(cpSep)];
      gameArgs = processArgs(vJson.minecraftArguments || '', vars);
    }

    const allArgs = [...jvmArgs, vJson.mainClass, ...gameArgs];

    gameProcess = execFile(javaExec, allArgs, { cwd: gameDir });

    // Hide launcher if setting enabled
    if (options.hideOnLaunch && mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.hide();
    }

    gameProcess.stdout?.on('data', data =>
      mainWindow && !mainWindow.isDestroyed() && mainWindow.webContents.send('game-log', data.toString()));
    gameProcess.stderr?.on('data', data =>
      mainWindow && !mainWindow.isDestroyed() && mainWindow.webContents.send('game-log', data.toString()));
    gameProcess.on('error', (err) => {
      gameProcess = null;
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.show();
        mainWindow.webContents.send('game-exit', { code: -1 });
        mainWindow.webContents.send('launch-progress', {
          step: 'error', current: 0, total: 1,
          message: `Java не найдена: ${err.message}. Установи Java 21.`,
        });
      }
    });
    gameProcess.on('exit', (code) => {
      gameProcess = null;
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.show();
        mainWindow.webContents.send('game-exit', { code });
      }
    });

    send('launch', 1, 1, 'Minecraft запущен!');
    return { success: true, message: 'Minecraft запущен!' };

  } catch (e) {
    gameProcess = null;
    send('error', 0, 1, `Ошибка: ${e.message}`);
    return { success: false, message: e.message };
  }
});

// ── Helpers ───────────────────────────────────────────────────
function defaultGameDir() {
  return process.platform === 'win32'
    ? path.join(process.env.APPDATA || os.homedir(), '.minecraft')
    : path.join(os.homedir(), '.minecraft');
}

function resolveGameDir(raw) {
  const trimmed = raw?.trim() || '';
  if (!trimmed) return defaultGameDir();
  // Expand leading ~ to home directory (bash-style paths on Windows won't work otherwise)
  if (trimmed.startsWith('~')) return path.join(os.homedir(), trimmed.slice(1));
  return trimmed;
}

function normalizeModLoader(loader) {
  const normalized = String(loader || '').toLowerCase();
  return ['fabric', 'forge', 'neoforge'].includes(normalized) ? normalized : 'fabric';
}

function loaderLabel(loader) {
  const normalized = normalizeModLoader(loader);
  return normalized === 'neoforge'
    ? 'NeoForge'
    : normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function findInstalledLoaderProfileId(type, mcVersion, gameDir) {
  const versionsDir = path.join(gameDir, 'versions');
  let dirs = [];
  try { dirs = fs.readdirSync(versionsDir); } catch {}

  if (type === 'fabric') {
    return dirs.find(d => d.startsWith('fabric-loader-') && d.endsWith(`-${mcVersion}`)) || null;
  }

  if (type === 'forge') {
    return dirs.find(d => d.startsWith(`${mcVersion}-forge`) || d.startsWith(`${mcVersion}-Forge`)) || null;
  }

  if (type === 'neoforge') {
    const vp = mcVersion.split('.');
    const nfPfx = vp.length >= 3 ? `neoforge-${vp[1]}.${vp[2]}.` : 'neoforge-';
    return dirs.find(d => d.startsWith(nfPfx)) || null;
  }

  return null;
}

function getInstalledLoaderStatus(type, mcVersion, gameDir) {
  const versionId = findInstalledLoaderProfileId(type, mcVersion, gameDir);
  return versionId
    ? { installed: true, versionId }
    : { installed: false };
}

function mergeLoaderProfileIntoVersionJson(vJson, loaderJson) {
  vJson.libraries = [...(loaderJson.libraries || []), ...(vJson.libraries || [])];
  if (loaderJson.mainClass) vJson.mainClass = loaderJson.mainClass;
  if (!vJson.arguments) vJson.arguments = {};

  if (loaderJson.arguments?.jvm) {
    vJson.arguments.jvm = [
      ...(Array.isArray(loaderJson.arguments.jvm) ? loaderJson.arguments.jvm : []),
      ...(Array.isArray(vJson.arguments.jvm) ? vJson.arguments.jvm : []),
    ];
  }

  if (loaderJson.arguments?.game) {
    vJson.arguments.game = [
      ...(Array.isArray(vJson.arguments.game) ? vJson.arguments.game : []),
      ...(Array.isArray(loaderJson.arguments.game) ? loaderJson.arguments.game : []),
    ];
  }

  if (loaderJson.minecraftArguments) {
    vJson.minecraftArguments = (loaderJson.minecraftArguments + ' ' + (vJson.minecraftArguments || '')).trim();
    delete vJson.arguments;
  }
}

function sanitizeProfileSegment(value) {
  return String(value || 'default').replace(/[^a-zA-Z0-9._-]+/g, '-');
}

function getModsProfileKey(mcVersion, loader) {
  return `${sanitizeProfileSegment(mcVersion)}-${sanitizeProfileSegment(loader)}`;
}

function getModsProfileDir(gameDir, mcVersion, loader) {
  return path.join(
    gameDir,
    'fmclient',
    'mod-profiles',
    getModsProfileKey(mcVersion, normalizeModLoader(loader)),
    'mods',
  );
}

function getModsProfileStatePath(gameDir) {
  return path.join(gameDir, 'fmclient', 'active-mod-profile.json');
}

function readActiveModsProfile(gameDir) {
  try {
    return JSON.parse(fs.readFileSync(getModsProfileStatePath(gameDir), 'utf8'));
  } catch {
    return null;
  }
}

function writeActiveModsProfile(gameDir, state) {
  const statePath = getModsProfileStatePath(gameDir);
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  fs.writeFileSync(statePath, JSON.stringify(state, null, 2));
}

function listModArchiveFiles(modsDir) {
  try {
    if (!fs.existsSync(modsDir)) return [];
    return fs.readdirSync(modsDir).filter(file => /\.(jar|zip)$/i.test(file));
  } catch {
    return [];
  }
}

function replaceModsDirectoryContents(sourceDir, targetDir, { exclude = [] } = {}) {
  const excluded = new Set(exclude.map(name => name.toLowerCase()));
  fs.mkdirSync(sourceDir, { recursive: true });
  fs.mkdirSync(targetDir, { recursive: true });

  for (const file of listModArchiveFiles(targetDir)) {
    if (excluded.has(file.toLowerCase())) continue;
    try { fs.unlinkSync(path.join(targetDir, file)); } catch {}
  }

  for (const file of listModArchiveFiles(sourceDir)) {
    if (excluded.has(file.toLowerCase())) continue;
    fs.copyFileSync(path.join(sourceDir, file), path.join(targetDir, file));
  }
}

function activateProfileMods(gameDir, mcVersion, loader) {
  const normalizedLoader = normalizeModLoader(loader);
  const currentKey = getModsProfileKey(mcVersion, normalizedLoader);
  const rootModsDir = path.join(gameDir, 'mods');
  const currentProfileDir = getModsProfileDir(gameDir, mcVersion, normalizedLoader);
  const activeProfile = readActiveModsProfile(gameDir);

  fs.mkdirSync(rootModsDir, { recursive: true });
  fs.mkdirSync(currentProfileDir, { recursive: true });

  if (activeProfile?.key && activeProfile.key !== currentKey && activeProfile.mcVersion && activeProfile.loader) {
    const previousProfileDir = getModsProfileDir(gameDir, activeProfile.mcVersion, activeProfile.loader);
    replaceModsDirectoryContents(rootModsDir, previousProfileDir, { exclude: ['fmvisuals.jar'] });
  } else {
    const bootstrapMods = listModArchiveFiles(rootModsDir).filter(file => file.toLowerCase() !== 'fmvisuals.jar');
    if (bootstrapMods.length && !listModArchiveFiles(currentProfileDir).length) {
      replaceModsDirectoryContents(rootModsDir, currentProfileDir, { exclude: ['fmvisuals.jar'] });
    }
  }

  replaceModsDirectoryContents(currentProfileDir, rootModsDir);
  writeActiveModsProfile(gameDir, {
    key: currentKey,
    mcVersion,
    loader: normalizedLoader,
    updatedAt: Date.now(),
  });

  return {
    profileModsDir: currentProfileDir,
    modCount: listModArchiveFiles(currentProfileDir).length,
  };
}

function buildModrinthVersionsUrl(projectId, mcVersion, loader) {
  let url = `https://api.modrinth.com/v2/project/${projectId}/version`;
  const params = new URLSearchParams();
  if (mcVersion) params.set('game_versions', JSON.stringify([mcVersion]));
  if (loader && loader !== 'all') params.set('loaders', JSON.stringify([normalizeModLoader(loader)]));
  if (params.toString()) url += `?${params.toString()}`;
  return url;
}

function pickPrimaryModFile(version) {
  return version?.files?.find(file => file.primary) || version?.files?.[0] || null;
}

function serializeModVersion(version) {
  const primaryFile = pickPrimaryModFile(version);
  return {
    id: version.id,
    name: version.name || version.version_number || 'Без названия',
    versionNumber: version.version_number || version.name || 'unknown',
    published: version.date_published || '',
    downloads: version.downloads || 0,
    featured: !!version.featured,
    loaders: Array.isArray(version.loaders) ? version.loaders : [],
    gameVersions: Array.isArray(version.game_versions) ? version.game_versions : [],
    fileName: primaryFile?.filename || '',
  };
}

async function installLoaderProfile({ type, mcVersion, gameDir, javaExec, onProgress }) {
  const sendP = typeof onProgress === 'function' ? onProgress : () => {};

  if (type === 'fabric') {
    sendP('Получение версий Fabric...', 5);
    const loaders = await httpRequest('GET',
      `https://meta.fabricmc.net/v2/versions/loader/${mcVersion}`,
      { 'User-Agent': 'FMclient' }, null);
    if (!Array.isArray(loaders) || !loaders.length)
      throw new Error(`Fabric не поддерживает Minecraft ${mcVersion}`);

    const loaderVersion = loaders[0].loader.version;
    sendP(`Загрузка профиля Fabric ${loaderVersion}...`, 20);

    const profileJson = await httpRequest('GET',
      `https://meta.fabricmc.net/v2/versions/loader/${mcVersion}/${loaderVersion}/profile/json`,
      { 'User-Agent': 'FMclient' }, null);

    const versionId = profileJson.id;
    const versionDir = path.join(gameDir, 'versions', versionId);
    fs.mkdirSync(versionDir, { recursive: true });
    fs.writeFileSync(path.join(versionDir, `${versionId}.json`), JSON.stringify(profileJson, null, 2));

    const libs = profileJson.libraries || [];
    const libTasks = [];
    for (const lib of libs) {
      if (!lib.name) continue;
      const parts = lib.name.split(':');
      if (parts.length < 3) continue;
      const [grp, art, ver] = parts;
      const libRelPath = `${grp.replace(/\./g, '/')}/${art}/${ver}/${art}-${ver}.jar`;
      const libPath = path.join(gameDir, 'libraries', libRelPath);
      if (!fs.existsSync(libPath) && lib.url) {
        const dlUrl = `${lib.url.replace(/\/$/, '')}/${libRelPath}`;
        libTasks.push({ libPath, dlUrl });
      }
    }

    let done = 0;
    for (const task of libTasks) {
      done += 1;
      sendP(`Библиотеки Fabric: ${done}/${libTasks.length}`, 20 + Math.round((done / Math.max(libTasks.length, 1)) * 70));
      try { await downloadFile(task.dlUrl, task.libPath, null); } catch {}
    }

    fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true });
    sendP('Fabric установлен!', 100);
    return { versionId };
  }

  if (type === 'forge') {
    sendP('Получение версий Forge...', 5);
    const meta = await httpRequest('GET',
      'https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json',
      { 'User-Agent': 'FMclient' }, null);
    const versions = meta[mcVersion];
    if (!versions?.length) throw new Error(`Forge не поддерживает Minecraft ${mcVersion}`);
    const forgeVersion = versions[versions.length - 1];

    sendP(`Загрузка установщика Forge ${forgeVersion}...`, 10);
    const installerUrl = `https://maven.minecraftforge.net/net/minecraftforge/forge/${forgeVersion}/forge-${forgeVersion}-installer.jar`;
    const installerPath = path.join(gameDir, 'forge-installer.jar');
    fs.mkdirSync(gameDir, { recursive: true });
    await downloadFile(installerUrl, installerPath, (done, total) =>
      sendP(`Forge installer: ${Math.round(done / total * 100)}%`, 10 + Math.round(done / total * 65)));

    sendP('Установка Forge... (1–2 минуты)', 75);
    await new Promise((resolve, reject) => {
      const proc = execFile(javaExec, ['-jar', installerPath, '--installClient', gameDir], { cwd: gameDir });
      proc.on('exit', code => {
        try { fs.unlinkSync(installerPath); } catch {}
        if (code === 0) resolve(); else reject(new Error(`Installer завершился с кодом ${code}`));
      });
      proc.on('error', reject);
    });

    fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true });
    sendP('Forge установлен!', 100);
    return { versionId: findInstalledLoaderProfileId(type, mcVersion, gameDir) };
  }

  if (type === 'neoforge') {
    sendP('Получение версий NeoForge...', 5);
    const metaXml = await new Promise((resolve, reject) => {
      const req = https.get(
        'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml',
        { headers: { 'User-Agent': 'FMclient' } },
        res => { let data = ''; res.on('data', chunk => data += chunk); res.on('end', () => resolve(data)); }
      );
      req.on('error', reject);
    });

    const allVersions = [...metaXml.matchAll(/<version>([^<]+)<\/version>/g)].map(match => match[1]);
    const vp = mcVersion.split('.');
    const nfPfx = vp.length >= 3 ? `${vp[1]}.${vp[2]}.` : `${vp[1]}.0.`;
    const compatible = allVersions.filter(v => v.startsWith(nfPfx));
    if (!compatible.length) throw new Error(`NeoForge не поддерживает Minecraft ${mcVersion}`);
    const nfVersion = compatible[compatible.length - 1];

    sendP(`Загрузка установщика NeoForge ${nfVersion}...`, 10);
    const installerUrl = `https://maven.neoforged.net/releases/net/neoforged/neoforge/${nfVersion}/neoforge-${nfVersion}-installer.jar`;
    const installerPath = path.join(gameDir, 'neoforge-installer.jar');
    fs.mkdirSync(gameDir, { recursive: true });
    await downloadFile(installerUrl, installerPath, (done, total) =>
      sendP(`NeoForge installer: ${Math.round(done / total * 100)}%`, 10 + Math.round(done / total * 65)));

    sendP('Установка NeoForge... (1–2 минуты)', 75);
    await new Promise((resolve, reject) => {
      const proc = execFile(javaExec, ['-jar', installerPath, '--installClient', gameDir], { cwd: gameDir });
      proc.on('exit', code => {
        try { fs.unlinkSync(installerPath); } catch {}
        if (code === 0) resolve(); else reject(new Error(`Installer завершился с кодом ${code}`));
      });
      proc.on('error', reject);
    });

    fs.mkdirSync(path.join(gameDir, 'mods'), { recursive: true });
    sendP('NeoForge установлен!', 100);
    return { versionId: findInstalledLoaderProfileId(type, mcVersion, gameDir) };
  }

  throw new Error(`Неизвестный тип загрузчика: ${type}`);
}

async function ensureLoaderInstalled({ type, mcVersion, gameDir, javaExec, onProgress }) {
  const existing = getInstalledLoaderStatus(type, mcVersion, gameDir);
  if (existing.installed) {
    if (typeof onProgress === 'function') {
      onProgress(`${loaderLabel(type)} уже установлен`, 100);
    }
    return existing;
  }

  const installed = await installLoaderProfile({ type, mcVersion, gameDir, javaExec, onProgress });
  const versionId = installed.versionId || findInstalledLoaderProfileId(type, mcVersion, gameDir);
  return { installed: !!versionId, versionId };
}

// ── IPC: Install mod loader ───────────────────────────────────
ipcMain.handle('install-loader', async (event, { type, mcVersion, gameDir: gameDirOpt, javaPath: javaPathOpt }) => {
  const gameDir  = resolveGameDir(gameDirOpt);
  const javaExec = javaPathOpt?.trim() || detectedJavaPath || 'java';

  const sendP = (msg, pct) => {
    if (mainWindow && !mainWindow.isDestroyed())
      mainWindow.webContents.send('loader-progress', { msg, pct });
  };

  try {
    const result = await installLoaderProfile({ type, mcVersion, gameDir, javaExec, onProgress: sendP });
    return { success: true, ...result };
  } catch (e) {
    return { success: false, error: e.message };
  }
});

// ── IPC: Check installed loaders ──────────────────────────────
ipcMain.handle('check-loaders', (event, { mcVersion, gameDir: gameDirOpt }) => {
  const gameDir     = resolveGameDir(gameDirOpt);
  return {
    fabric:   getInstalledLoaderStatus('fabric', mcVersion, gameDir),
    forge:    getInstalledLoaderStatus('forge', mcVersion, gameDir),
    neoforge: getInstalledLoaderStatus('neoforge', mcVersion, gameDir),
  };
});

// ── IPC: Modrinth search ──────────────────────────────────────
ipcMain.handle('search-mods', async (event, { query, mcVersion, loader, offset = 0 }) => {
  try {
    const facets = [['project_type:mod']];
    if (mcVersion) facets.push([`versions:${mcVersion}`]);
    if (loader && loader !== 'all') facets.push([`categories:${loader}`]);

    const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20&offset=${offset}&index=relevance`;
    const data = await httpRequest('GET', url, { 'User-Agent': FMCLIENT_UA }, null);
    return { success: true, hits: data.hits || [], total: data.total_hits || 0 };
  } catch (e) {
    return { success: false, error: e.message };
  }
});

// ── IPC: Get compatible mod versions ──────────────────────────
ipcMain.handle('get-mod-versions', async (event, { projectId, mcVersion, loader }) => {
  try {
    const url = buildModrinthVersionsUrl(projectId, mcVersion, loader);
    const versions = await httpRequest('GET', url, { 'User-Agent': FMCLIENT_UA }, null);
    if (!Array.isArray(versions) || !versions.length) {
      return { success: true, versions: [] };
    }

    return {
      success: true,
      versions: versions.map(serializeModVersion),
    };
  } catch (e) {
    return { success: false, error: e.message, versions: [] };
  }
});

// ── IPC: Install mod ──────────────────────────────────────────
ipcMain.handle('install-mod', async (event, { projectId, versionId, mcVersion, loader, gameDir: gameDirOpt }) => {
  const gameDir = resolveGameDir(gameDirOpt);
  const normalizedLoader = normalizeModLoader(loader);
  const modsDir = getModsProfileDir(gameDir, mcVersion, normalizedLoader);
  fs.mkdirSync(modsDir, { recursive: true });

  try {
    let selectedVersion = null;
    if (versionId) {
      selectedVersion = await httpRequest(
        'GET',
        `https://api.modrinth.com/v2/version/${versionId}`,
        { 'User-Agent': FMCLIENT_UA },
        null,
      );
    } else {
      const versions = await httpRequest(
        'GET',
        buildModrinthVersionsUrl(projectId, mcVersion, normalizedLoader),
        { 'User-Agent': FMCLIENT_UA },
        null,
      );
      if (!Array.isArray(versions) || !versions.length) {
        throw new Error('Совместимая версия мода не найдена для выбранной MC версии и загрузчика');
      }
      selectedVersion = versions[0];
    }

    const primaryFile = pickPrimaryModFile(selectedVersion);
    if (!primaryFile) throw new Error('Файл мода не найден');

    const destPath = path.join(modsDir, primaryFile.filename);
    await downloadFile(primaryFile.url, destPath, (done, total) => {
      if (mainWindow && !mainWindow.isDestroyed())
        mainWindow.webContents.send('mod-download-progress', { filename: primaryFile.filename, done, total });
    });
    return {
      success: true,
      fileName: primaryFile.filename,
      versionName: selectedVersion.version_number || selectedVersion.name || '',
    };
  } catch (e) {
    return { success: false, error: e.message };
  }
});

// ── IPC: Get installed mods ───────────────────────────────────
ipcMain.handle('get-installed-mods', (event, { gameDir: gameDirOpt, mcVersion, loader }) => {
  const gameDir = resolveGameDir(gameDirOpt);
  const modsDir = getModsProfileDir(gameDir, mcVersion, normalizeModLoader(loader));
  try {
    if (!fs.existsSync(modsDir)) return { success: true, mods: [] };
    const mods = fs.readdirSync(modsDir)
      .filter(f => f.endsWith('.jar') || f.endsWith('.zip'))
      .map(f => {
        const stat = fs.statSync(path.join(modsDir, f));
        return { name: f, size: stat.size, modified: stat.mtimeMs };
      })
      .sort((a, b) => b.modified - a.modified);
    return { success: true, mods, profileDir: modsDir };
  } catch (e) {
    return { success: false, error: e.message, mods: [], profileDir: modsDir };
  }
});

// ── IPC: Delete mod ───────────────────────────────────────────
ipcMain.handle('delete-mod', (event, { filename, gameDir: gameDirOpt, mcVersion, loader }) => {
  const gameDir = resolveGameDir(gameDirOpt);
  try {
    const safeName = path.basename(filename);
    const modPath = path.join(getModsProfileDir(gameDir, mcVersion, normalizeModLoader(loader)), safeName);
    if (fs.existsSync(modPath)) fs.unlinkSync(modPath);
    return { success: true };
  } catch (e) {
    return { success: false, error: e.message };
  }
});

// ── IPC: Open mods folder ─────────────────────────────────────
ipcMain.handle('open-mods-folder', (event, { gameDir: gameDirOpt, mcVersion, loader }) => {
  const gameDir = resolveGameDir(gameDirOpt);
  const modsDir = getModsProfileDir(gameDir, mcVersion, normalizeModLoader(loader));
  fs.mkdirSync(modsDir, { recursive: true });
  shell.openPath(modsDir);
  return { success: true };
});
