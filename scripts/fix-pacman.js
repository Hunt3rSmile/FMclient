// Removes auto-detected dependencies from the generated .pacman file
// that are not actually required (e.g. http-parser removed from Arch repos)

const fs   = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const os   = require('os');

const REMOVE_DEPS = ['http-parser'];

const distDir  = path.join(__dirname, '..', 'dist');
const pkg      = fs.readdirSync(distDir).find(f => f.endsWith('.pacman'));
if (!pkg) { console.log('No .pacman found, skipping.'); process.exit(0); }

const pkgPath  = path.join(distDir, pkg);
const tmpDir   = fs.mkdtempSync(path.join(os.tmpdir(), 'fmclient-pkg-'));

console.log(`Fixing dependencies in ${pkg}...`);

// Extract
execSync(`tar -xJf "${pkgPath}" -C "${tmpDir}"`);

// Patch .PKGINFO
const pkgInfoPath = path.join(tmpDir, '.PKGINFO');
let pkgInfo = fs.readFileSync(pkgInfoPath, 'utf8');
for (const dep of REMOVE_DEPS) {
  const before = pkgInfo;
  pkgInfo = pkgInfo.replace(new RegExp(`^depend = ${dep}\\n`, 'm'), '');
  if (pkgInfo !== before) console.log(`  Removed dependency: ${dep}`);
}
fs.writeFileSync(pkgInfoPath, pkgInfo);

// Repack
fs.unlinkSync(pkgPath);
execSync(`tar -cJf "${pkgPath}" -C "${tmpDir}" .`);

// Cleanup
fs.rmSync(tmpDir, { recursive: true });
console.log(`Done: ${pkg}`);
