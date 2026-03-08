const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  minimize:        () => ipcRenderer.send('window-minimize'),
  close:           () => ipcRenderer.send('window-close'),
  maximize:        () => ipcRenderer.send('window-maximize'),
  getSystemInfo:   () => ipcRenderer.invoke('get-system-info'),
  detectJava:      () => ipcRenderer.invoke('detect-java'),
  launchGame:      (opts) => ipcRenderer.invoke('launch-game', opts),
  getVersions:     () => ipcRenderer.invoke('get-versions'),
  checkVersion:    (opts) => ipcRenderer.invoke('check-version', opts),
  deleteVersion:   (opts) => ipcRenderer.invoke('delete-version', opts),
  // Microsoft auth
  authMicrosoft:   () => ipcRenderer.invoke('auth-microsoft'),
  authElyby:       (creds) => ipcRenderer.invoke('auth-elyby', creds),
  authRefresh:     () => ipcRenderer.invoke('auth-refresh'),
  authGetStored:   () => ipcRenderer.invoke('auth-get-stored'),
  authLogout:      () => ipcRenderer.invoke('auth-logout'),
  // Events from main process
  onLaunchProgress: (cb) => ipcRenderer.on('launch-progress', (_, d) => cb(d)),
  onGameLog:        (cb) => ipcRenderer.on('game-log',        (_, d) => cb(d)),
  onGameExit:       (cb) => ipcRenderer.on('game-exit',       (_, d) => cb(d)),
  onDebugLog:       (cb) => ipcRenderer.on('debug-log',       (_, d) => cb(d)),
  offLaunchProgress: () => ipcRenderer.removeAllListeners('launch-progress'),
  offGameLog:        () => ipcRenderer.removeAllListeners('game-log'),
  offGameExit:       () => ipcRenderer.removeAllListeners('game-exit'),
  offDebugLog:       () => ipcRenderer.removeAllListeners('debug-log'),
  // Auto-update
  checkUpdate:       () => ipcRenderer.invoke('check-update'),
  startUpdate:       () => ipcRenderer.invoke('start-update'),
  openRelease:       (url) => ipcRenderer.invoke('open-release', url),
  onUpdateAvailable: (cb) => ipcRenderer.on('update-available', (_, d) => cb(d)),
  onUpdateStatus:    (cb) => ipcRenderer.on('update-status', (_, d) => cb(d)),
  offUpdateStatus:   () => ipcRenderer.removeAllListeners('update-status'),
  // Mod loaders
  installLoader:     (opts) => ipcRenderer.invoke('install-loader', opts),
  checkLoaders:      (opts) => ipcRenderer.invoke('check-loaders', opts),
  onLoaderProgress:  (cb)  => ipcRenderer.on('loader-progress', (_, d) => cb(d)),
  // Mods (Modrinth)
  searchMods:        (opts) => ipcRenderer.invoke('search-mods', opts),
  getModVersions:    (opts) => ipcRenderer.invoke('get-mod-versions', opts),
  installMod:        (opts) => ipcRenderer.invoke('install-mod', opts),
  getInstalledMods:  (opts) => ipcRenderer.invoke('get-installed-mods', opts),
  deleteMod:         (opts) => ipcRenderer.invoke('delete-mod', opts),
  openModsFolder:    (opts) => ipcRenderer.invoke('open-mods-folder', opts),
});
