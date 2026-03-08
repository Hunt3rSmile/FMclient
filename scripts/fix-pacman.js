// Removes auto-detected dependencies from the generated .pacman file
// that are not actually required (e.g. http-parser removed from Arch repos)

const fs   = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const os   = require('os');

const REMOVE_DEPS = ['http-parser'];

const distDir  = path.join(__dirname, '..', 'dist');
const pkg      = fs.readdirSync(distDir).find(f => f.endsWith('.pacman'));
if (!pkg) { console.log('No .pacman found, skipping.'); process.exit(0); }

const pkgPath  = path.join(distDir, pkg);
const tmpDir   = fs.mkdtempSync(path.join(os.tmpdir(), 'fmclient-pkg-'));

console.log(`Fixing dependencies in ${pkg}...`);

// Extract with bsdtar so pacman metadata files keep their exact names
execFileSync('bsdtar', ['-xJf', pkgPath, '-C', tmpDir], { stdio: 'inherit' });

// Patch .PKGINFO
const pkgInfoPath = path.join(tmpDir, '.PKGINFO');
let pkgInfo = fs.readFileSync(pkgInfoPath, 'utf8');
for (const dep of REMOVE_DEPS) {
  const before = pkgInfo;
  pkgInfo = pkgInfo.replace(new RegExp(`^depend = ${dep}\\n`, 'm'), '');
  if (pkgInfo !== before) console.log(`  Removed dependency: ${dep}`);
}
fs.writeFileSync(pkgInfoPath, pkgInfo);

// Repack without the leading "./" prefix. pacman expects metadata entries
// like ".PKGINFO" at the archive root and may reject "./.PKGINFO".
const rootEntries = fs.readdirSync(tmpDir).sort((a, b) => {
  const aMeta = a.startsWith('.') ? 0 : 1;
  const bMeta = b.startsWith('.') ? 0 : 1;
  if (aMeta !== bMeta) return aMeta - bMeta;
  return a.localeCompare(b);
});

fs.unlinkSync(pkgPath);
execFileSync('bsdtar', ['-cJf', pkgPath, '-C', tmpDir, ...rootEntries], { stdio: 'inherit' });

// Cleanup
fs.rmSync(tmpDir, { recursive: true });
console.log(`Done: ${pkg}`);
