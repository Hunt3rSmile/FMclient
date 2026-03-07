/* ============================================================
   FMclient — Renderer Process
   ============================================================ */

// ── Splash Screen ────────────────────────────────────────────
const splash = document.getElementById('splash');

setTimeout(() => {
  splash.classList.add('hide');
  setTimeout(() => splash.classList.add('gone'), 650);
}, 2400);

// ── Particle Background ──────────────────────────────────────
const canvas = document.getElementById('bg-canvas');
const ctx = canvas.getContext('2d');

const PARTICLE_COUNT = 65;
const MAX_DIST = 130;
let particles = [];

function resizeCanvas() {
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
}

const PARTICLE_COLORS = [
  '168,85,247',
  '236,72,153',
  '192,132,252',
  '244,114,182',
  '129,140,248',
];

class Particle {
  constructor() { this.init(); }
  init() {
    this.x     = Math.random() * canvas.width;
    this.y     = Math.random() * canvas.height;
    this.vx    = (Math.random() - 0.5) * 0.45;
    this.vy    = (Math.random() - 0.5) * 0.45;
    this.r     = Math.random() * 1.6 + 0.4;
    this.a     = Math.random() * 0.40 + 0.06;
    this.color = PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)];
  }
  update() {
    this.x += this.vx; this.y += this.vy;
    if (this.x < 0) this.x = canvas.width;
    if (this.x > canvas.width) this.x = 0;
    if (this.y < 0) this.y = canvas.height;
    if (this.y > canvas.height) this.y = 0;
  }
  draw() {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(${this.color},${this.a})`;
    ctx.fill();
  }
}

function drawLines() {
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x;
      const dy = particles[i].y - particles[j].y;
      const d  = Math.sqrt(dx * dx + dy * dy);
      if (d < MAX_DIST) {
        ctx.beginPath();
        ctx.moveTo(particles[i].x, particles[i].y);
        ctx.lineTo(particles[j].x, particles[j].y);
        ctx.strokeStyle = `rgba(168,85,247,${(1 - d / MAX_DIST) * 0.08})`;
        ctx.lineWidth = 0.5;
        ctx.stroke();
      }
    }
  }
}

function animateParticles() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  particles.forEach(p => { p.update(); p.draw(); });
  drawLines();
  requestAnimationFrame(animateParticles);
}

resizeCanvas();
particles = Array.from({ length: PARTICLE_COUNT }, () => new Particle());
animateParticles();
window.addEventListener('resize', () => { resizeCanvas(); });


// ── Window Controls ──────────────────────────────────────────
document.getElementById('btn-minimize').addEventListener('click', () =>
  window.electronAPI?.minimize()
);
document.getElementById('btn-close').addEventListener('click', () =>
  window.electronAPI?.close()
);


// ── Auto-Update Banner ───────────────────────────────────────
let updateReleaseUrl = null;

window.electronAPI?.onUpdateAvailable(({ version, url }) => {
  updateReleaseUrl = url;
  document.getElementById('update-version').textContent = `v${version}`;
  document.getElementById('update-banner').classList.add('visible');
});

document.getElementById('update-download-btn')?.addEventListener('click', () => {
  if (updateReleaseUrl) window.electronAPI?.openRelease(updateReleaseUrl);
});

document.getElementById('update-dismiss-btn')?.addEventListener('click', () => {
  document.getElementById('update-banner').classList.remove('visible');
});

// ── Tab Navigation ───────────────────────────────────────────
document.querySelectorAll('.nav-item').forEach(btn => {
  btn.addEventListener('click', () => {
    const tab = btn.dataset.tab;
    document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(`tab-${tab}`)?.classList.add('active');
  });
});


// ── Profile & Username ───────────────────────────────────────
const usernameDisplay  = document.getElementById('username-display');
const welcomeUsername  = document.getElementById('welcome-username');
const profileEdit      = document.getElementById('profile-edit');
const usernameInput    = document.getElementById('username-input');
const saveUsernameBtn  = document.getElementById('save-username-btn');
const editProfileBtn   = document.getElementById('edit-profile-btn');

// Sanitize stored username — strip non-ASCII on load (fixes Cyrillic default crash)
const _storedName   = localStorage.getItem('mc_username') || '';
const _sanitizedName = _storedName.replace(/[^a-zA-Z0-9_]/g, '');
let username = _sanitizedName.length >= 3 ? _sanitizedName : 'Player';

function setUsername(name) {
  username = name;
  usernameDisplay.textContent = name;
  welcomeUsername.textContent = name;
}

setUsername(username);
usernameInput.value = username;

editProfileBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  profileEdit.classList.toggle('open');
  if (profileEdit.classList.contains('open')) {
    usernameInput.focus();
    usernameInput.select();
  }
});

saveUsernameBtn.addEventListener('click', doSaveUsername);
usernameInput.addEventListener('keydown', e => { if (e.key === 'Enter') doSaveUsername(); });

function doSaveUsername() {
  const val = usernameInput.value.trim().replace(/[^a-zA-Z0-9_]/g, '');
  if (!val || val.length < 3) {
    showToast('Ник: 3-16 символов (латиница, цифры, _)');
    return;
  }
  localStorage.setItem('mc_username', val);
  setUsername(val);
  profileEdit.classList.remove('open');
  showToast('Профиль сохранён');
}


// ── Version Selector ─────────────────────────────────────────
function makeSelect(selectorId, selectedId, textId, optionsId, storageKey, onSelect) {
  const selected = document.getElementById(selectedId);
  const text     = document.getElementById(textId);
  const options  = document.getElementById(optionsId);
  let current    = localStorage.getItem(storageKey);

  if (current) {
    const opt = options.querySelector(`[data-value="${current}"]`);
    if (opt) {
      text.textContent = opt.textContent;
      options.querySelectorAll('.select-option').forEach(o => o.classList.remove('active'));
      opt.classList.add('active');
    } else {
      current = null;
    }
  }
  if (!current) {
    current = options.querySelector('.select-option.active')?.dataset.value || '';
  }

  selected.addEventListener('click', e => {
    e.stopPropagation();
    const open = options.classList.contains('open');
    document.querySelectorAll('.select-options').forEach(o => o.classList.remove('open'));
    document.querySelectorAll('.select-selected').forEach(o => o.classList.remove('open'));
    if (!open) {
      options.classList.add('open');
      selected.classList.add('open');
    }
  });

  options.querySelectorAll('.select-option').forEach(opt => {
    opt.addEventListener('click', () => {
      current = opt.dataset.value;
      text.textContent = opt.textContent;
      options.querySelectorAll('.select-option').forEach(o => o.classList.remove('active'));
      opt.classList.add('active');
      options.classList.remove('open');
      selected.classList.remove('open');
      localStorage.setItem(storageKey, current);
      if (onSelect) onSelect(current);
    });
  });

  document.addEventListener('click', () => {
    options.classList.remove('open');
    selected.classList.remove('open');
  });

  return () => current;
}

const getVersion = makeSelect(
  'version-selector', 'version-selected', 'version-text', 'version-options',
  'mc_version',
  v => {
    document.getElementById('stat-version').textContent = v;
    refreshDeleteBtn(v);
  }
);
document.getElementById('stat-version').textContent = getVersion();

// ── Delete version button ─────────────────────────────────────
const deleteVersionBtn = document.getElementById('delete-version-btn');

async function refreshDeleteBtn(version) {
  if (!version) { deleteVersionBtn.style.display = 'none'; return; }
  const gameDir = document.getElementById('game-dir')?.value?.trim() || '';
  const exists  = await window.electronAPI?.checkVersion({ version, gameDir });
  deleteVersionBtn.style.display = exists ? 'flex' : 'none';
}

// Check on startup
refreshDeleteBtn(getVersion());

deleteVersionBtn.addEventListener('click', async (e) => {
  e.stopPropagation();
  const version = getVersion();
  if (!version) return;

  // Inline confirmation: first click → warning state, second click → delete
  if (!deleteVersionBtn.dataset.confirming) {
    deleteVersionBtn.dataset.confirming = '1';
    deleteVersionBtn.classList.add('confirming');
    deleteVersionBtn.title = `Удалить ${version}? Нажми ещё раз`;
    showToast(`Удалить ${version}? Нажми кнопку ещё раз для подтверждения`);
    setTimeout(() => {
      deleteVersionBtn.dataset.confirming = '';
      deleteVersionBtn.classList.remove('confirming');
      deleteVersionBtn.title = 'Удалить версию';
    }, 4000);
    return;
  }

  deleteVersionBtn.dataset.confirming = '';
  deleteVersionBtn.classList.remove('confirming');
  deleteVersionBtn.title = 'Удалить версию';
  deleteVersionBtn.disabled = true;

  const gameDir = document.getElementById('game-dir')?.value?.trim() || '';
  const result  = await window.electronAPI?.deleteVersion({ version, gameDir });

  if (result?.success) {
    showToast(`Версия ${version} удалена`);
    deleteVersionBtn.style.display = 'none';
  } else {
    showToast(result?.message || 'Ошибка удаления');
  }
  deleteVersionBtn.disabled = false;
});

const getType = makeSelect(
  'type-selector', 'type-selected', 'type-text', 'type-options',
  'mc_loader', null
);



// ── RAM Slider ───────────────────────────────────────────────
const ramSlider  = document.getElementById('ram-slider');
const ramValue   = document.getElementById('ram-value');
const statRam    = document.getElementById('stat-ram');
const ramPresets = document.querySelectorAll('.ram-preset');

let ram = parseInt(localStorage.getItem('mc_ram') || '2048');
ramSlider.value = ram;
updateRamUI();

ramSlider.addEventListener('input', () => {
  ram = parseInt(ramSlider.value);
  updateRamUI();
  localStorage.setItem('mc_ram', ram);
});

ramPresets.forEach(btn => {
  btn.addEventListener('click', () => {
    ram = parseInt(btn.dataset.value);
    ramSlider.value = ram;
    updateRamUI();
    localStorage.setItem('mc_ram', ram);
  });
});

function updateRamUI() {
  ramValue.textContent = ram;
  statRam.textContent = ram >= 1024 ? (ram / 1024).toFixed(ram % 1024 === 0 ? 0 : 1) + ' ГБ' : ram + ' МБ';
  const pct = ((ram - parseInt(ramSlider.min)) / (parseInt(ramSlider.max) - parseInt(ramSlider.min))) * 100;
  ramSlider.style.background = `linear-gradient(to right, #a855f7 0%, #ec4899 ${pct}%, rgba(255,255,255,0.08) ${pct}%)`;
  ramPresets.forEach(btn => btn.classList.toggle('active', parseInt(btn.dataset.value) === ram));
}

window.electronAPI?.getSystemInfo().then(info => {
  if (!info) return;
  const maxRam = Math.max(1024, Math.min(info.totalMemory - 1024, 32768));
  ramSlider.max = maxRam;
  const lastPreset = ramPresets[ramPresets.length - 1];
  if (parseInt(lastPreset.dataset.value) > maxRam) {
    lastPreset.dataset.value = maxRam;
    lastPreset.textContent = (maxRam / 1024) + 'G';
  }
  document.getElementById('stat-java').textContent = `${Math.round(info.totalMemory / 1024)} ГБ ОЗУ`;
  updateRamUI();
}).catch(() => {});


// ── Auth (Microsoft + ely.by) ─────────────────────────────────
const msLoginBtn  = document.getElementById('ms-login-btn');
const msLogged    = document.getElementById('ms-logged');
const msLogoutBtn = document.getElementById('ms-logout-btn');
const msBadgeLabel = document.getElementById('ms-badge-label');
let isLoggedIn = false;

function setAuthState(loggedIn, mcUsername, authType) {
  isLoggedIn = loggedIn;
  msLoginBtn.style.display  = loggedIn ? 'none' : 'flex';
  msLogged.style.display    = loggedIn ? 'flex'  : 'none';
  // Show ely.by toggle only when not logged in
  const elybyAuth = document.getElementById('elyby-auth');
  elybyAuth.style.display = loggedIn ? 'none' : 'block';

  if (loggedIn && mcUsername) {
    setUsername(mcUsername);
    document.getElementById('edit-profile-btn').style.display = 'none';
    if (msBadgeLabel) {
      msBadgeLabel.textContent = authType === 'elyby' ? 'ely.by' : 'Лицензия';
    }
  } else {
    document.getElementById('edit-profile-btn').style.display = '';
  }
}

window.electronAPI?.authRefresh().then(result => {
  if (result?.success) {
    setAuthState(true, result.username, 'msa');
    showToast(`Добро пожаловать, ${result.username}!`);
  } else {
    window.electronAPI?.authGetStored().then(stored => {
      if (stored) setAuthState(true, stored.username, stored.type || 'msa');
    });
  }
}).catch(() => {});

msLoginBtn.addEventListener('click', async () => {
  msLoginBtn.disabled = true;
  msLoginBtn.textContent = 'Открываю Microsoft...';
  const result = await window.electronAPI?.authMicrosoft();
  if (result?.success) {
    setAuthState(true, result.username, 'msa');
    showToast(`Вошёл как ${result.username}`);
  } else {
    showToast(result?.error || 'Ошибка входа');
    msLoginBtn.disabled = false;
    msLoginBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 21 21" fill="none"><rect x="1" y="1" width="9" height="9" fill="#f25022"/><rect x="11" y="1" width="9" height="9" fill="#7fba00"/><rect x="1" y="11" width="9" height="9" fill="#00a4ef"/><rect x="11" y="11" width="9" height="9" fill="#ffb900"/></svg> Войти через Microsoft`;
  }
});

msLogoutBtn.addEventListener('click', async () => {
  await window.electronAPI?.authLogout();
  setAuthState(false, null, null);
  const _n = (localStorage.getItem('mc_username') || '').replace(/[^a-zA-Z0-9_]/g, '');
  setUsername(_n.length >= 3 ? _n : 'Player');
  showToast('Вышел из аккаунта');
});

// ── ely.by auth ───────────────────────────────────────────────
const elybyToggleBtn  = document.getElementById('elyby-toggle-btn');
const elybyForm       = document.getElementById('elyby-form');
const elybyEmail      = document.getElementById('elyby-email');
const elybyPassword   = document.getElementById('elyby-password');
const elybySubmitBtn  = document.getElementById('elyby-submit-btn');

elybyToggleBtn.addEventListener('click', () => {
  elybyForm.classList.toggle('open');
  if (elybyForm.classList.contains('open')) elybyEmail.focus();
});

async function doElybyLogin() {
  const email = elybyEmail.value.trim();
  const pass  = elybyPassword.value;
  if (!email || !pass) { showToast('Введи логин и пароль'); return; }

  elybySubmitBtn.disabled = true;
  elybySubmitBtn.textContent = 'Вход...';

  const result = await window.electronAPI?.authElyby({ username: email, password: pass });
  if (result?.success) {
    setAuthState(true, result.username, 'elyby');
    elybyForm.classList.remove('open');
    elybyPassword.value = '';
    showToast(`Вошёл как ${result.username} (ely.by)`);
  } else {
    showToast(result?.error || 'Ошибка входа в ely.by');
  }

  elybySubmitBtn.disabled = false;
  elybySubmitBtn.textContent = 'Войти';
}

elybySubmitBtn.addEventListener('click', doElybyLogin);
elybyPassword.addEventListener('keydown', e => { if (e.key === 'Enter') doElybyLogin(); });


// ── Java Detection ───────────────────────────────────────────
const javaDot   = document.getElementById('java-dot');
const javaLabel = document.getElementById('java-label');
const statJava  = document.getElementById('stat-java');

let detectedJavaMajor = 0;

window.electronAPI?.detectJava().then(result => {
  if (result?.found) {
    detectedJavaMajor = result.major || 0;
    javaDot.classList.add('ok');
    const display = result.major ? `Java ${result.major}` : `Java ${result.version}`;
    javaLabel.textContent = display;
    statJava.textContent  = result.version;
    localStorage.setItem('detected_java_path', result.path || 'java');
    if (result.major && result.major < 17) {
      showToast(`Java ${result.major} обнаружена. Для MC 1.17+ нужна Java 17+`);
    }
  } else {
    javaDot.classList.add('fail');
    javaLabel.textContent = 'Java не найдена';
    statJava.textContent  = 'Нет';
    showToast('Java не найдена! Установи Java 21 для игры.');
  }
}).catch(() => { javaLabel.textContent = 'Java: ошибка'; });


// ── Tips Carousel ─────────────────────────────────────────────
const tipsCarousel = document.getElementById('tips-carousel');
const tipsDots     = document.getElementById('tips-dots');
const tipSlides    = tipsCarousel ? Array.from(tipsCarousel.querySelectorAll('.tip-slide')) : [];
let currentTip = 0;
let tipsTimer;

function initDots() {
  tipsDots.innerHTML = '';
  tipSlides.forEach((_, i) => {
    const dot = document.createElement('div');
    dot.className = 'tips-dot' + (i === 0 ? ' active' : '');
    dot.addEventListener('click', () => goToTip(i));
    tipsDots.appendChild(dot);
  });
}

function goToTip(index) {
  const prev = tipSlides[currentTip];
  prev.classList.remove('active');
  prev.classList.add('exit');
  setTimeout(() => prev.classList.remove('exit'), 500);
  currentTip = (index + tipSlides.length) % tipSlides.length;
  tipSlides[currentTip].classList.add('active');
  tipsDots.querySelectorAll('.tips-dot').forEach((d, i) =>
    d.classList.toggle('active', i === currentTip));
}

function startTipsAuto() {
  clearInterval(tipsTimer);
  tipsTimer = setInterval(() => goToTip(currentTip + 1), 5000);
}

if (tipSlides.length) {
  initDots();
  startTipsAuto();
  tipsCarousel.addEventListener('mouseenter', () => clearInterval(tipsTimer));
  tipsCarousel.addEventListener('mouseleave', startTipsAuto);
}


// ── Launch Progress UI ────────────────────────────────────────
const lpSection  = document.getElementById('launch-progress-section');
const lpStepName = document.getElementById('lp-step-name');
const lpMessage  = document.getElementById('lp-message');
const lpBar      = document.getElementById('lp-bar');
const lpPercent  = document.getElementById('lp-percent');
const lpStepIcon = document.getElementById('lp-step-icon');

const STEP_LABELS = {
  manifest:  'Манифест версий',
  client:    'Клиент Minecraft',
  libraries: 'Библиотеки',
  assets:    'Ресурсы',
  logo:      'Логотип FMclient',
  launch:    'Запуск',
  error:     'Ошибка',
};

function showProgress(visible) {
  const playControls = document.querySelector('.play-controls');
  lpSection.style.display   = visible ? 'flex' : 'none';
  playControls.style.display = visible ? 'none' : 'flex';
}

let isGameRunning = false;

window.electronAPI?.onLaunchProgress(data => {
  showProgress(true);

  const stepLabel = STEP_LABELS[data.step] || data.step;
  lpStepName.textContent = stepLabel;
  lpMessage.textContent  = data.message;

  if (data.step === 'error') {
    lpStepIcon.innerHTML = '<span style="color:#ef4444;font-size:18px;">✕</span>';
    lpBar.style.background = '#ef4444';
    lpPercent.textContent = '';
    setTimeout(() => {
      showProgress(false);
      resetPlayBtn();
      lpBar.style.background = '';
      lpStepIcon.innerHTML = '<div class="lp-spinner"></div>';
    }, 4000);
    return;
  }

  if (data.step === 'launch' && data.current === 1) {
    lpStepIcon.innerHTML = '<span style="color:#a855f7;font-size:18px;">✓</span>';
    lpPercent.textContent = '';
    lpBar.style.width = '100%';
    // After 1s, switch to "game running" state
    setTimeout(() => {
      showProgress(false);
      setGameRunningState(true);
      refreshDeleteBtn(getVersion()); // version now exists on disk
    }, 1200);
    return;
  }

  if (data.total > 0) {
    const pct = Math.round((data.current / data.total) * 100);
    lpBar.style.width     = pct + '%';
    lpPercent.textContent = pct + '%';
  } else {
    lpBar.style.width     = '0%';
    lpPercent.textContent = '';
  }
});

window.electronAPI?.onGameExit(data => {
  isGameRunning = false;
  setGameRunningState(false);
  resetPlayBtn();
  showToast(data.code === 0 ? 'Игра завершена' : `Игра завершилась с кодом ${data.code}`);
});


// ── Play Button ──────────────────────────────────────────────
const playBtn          = document.getElementById('play-btn');
const playBtnNormal   = playBtn.querySelector('.play-btn-normal');
const playBtnLoading  = playBtn.querySelector('.play-btn-loading');
const playBtnRunning  = playBtn.querySelector('.play-btn-running');

function resetPlayBtn() {
  playBtn.disabled              = false;
  playBtnNormal.style.display  = 'flex';
  playBtnLoading.style.display = 'none';
  playBtnRunning.style.display = 'none';
}

function setGameRunningState(running) {
  isGameRunning = running;
  playBtn.disabled = running;
  playBtnNormal.style.display  = running ? 'none' : 'flex';
  playBtnLoading.style.display = 'none';
  playBtnRunning.style.display = running ? 'flex' : 'none';
  playBtn.style.animation = running ? 'none' : '';
  if (running) {
    playBtn.style.background = 'linear-gradient(135deg, #1e1b4b, #3b1e6e)';
    playBtn.style.boxShadow  = '0 0 20px rgba(168,85,247,0.3)';
  } else {
    playBtn.style.background = '';
    playBtn.style.boxShadow  = '';
  }
}

playBtn.addEventListener('click', async () => {
  if (playBtn.disabled || isGameRunning) return;

  const version = document.getElementById('version-options')
    ?.querySelector('.select-option.active')?.dataset.value
    || localStorage.getItem('mc_version')
    || '1.21.11';
  const type = document.getElementById('type-options')
    ?.querySelector('.select-option.active')?.dataset.value
    || localStorage.getItem('mc_loader')
    || 'fabric';

  if (!username || !/^[a-zA-Z0-9_]{3,16}$/.test(username)) {
    showToast('Ник должен содержать только латиницу, цифры и _ (3–16 символов)');
    profileEdit.classList.add('open');
    usernameInput.focus();
    return;
  }

  // Check Java compatibility with selected MC version
  const _mcMinor = parseInt((version || '').split('.')[1] || '0');
  if (!document.getElementById('java-path')?.value?.trim() && detectedJavaMajor > 0 && detectedJavaMajor < 17 && _mcMinor >= 17) {
    showToast();
    return;
  }

  playBtn.disabled             = true;
  playBtnNormal.style.display  = 'none';
  playBtnLoading.style.display = 'flex';
  showProgress(true);

  // Reset bar
  lpBar.style.width     = '0%';
  lpPercent.textContent = '0%';
  lpStepName.textContent = 'Подготовка...';
  lpMessage.textContent  = 'Соединение с серверами Mojang';

  const gameDir  = document.getElementById('game-dir')?.value?.trim()  || '';
  const javaPath = document.getElementById('java-path')?.value?.trim() || '';

  showToast(`Запуск Minecraft ${version}...`);

  try {
    const hideOnLaunch = localStorage.getItem('toggle_hideOnLaunch') !== 'off';
    const result = await window.electronAPI?.launchGame({ version, type, username, ram, gameDir, javaPath, hideOnLaunch });
    if (!result?.success) {
      showToast(result?.message || 'Ошибка запуска');
      showProgress(false);
      resetPlayBtn();
    }
    // On success the progress events handle UI transition
  } catch (e) {
    showToast('Критическая ошибка запуска');
    showProgress(false);
    resetPlayBtn();
  }
});


// ── Settings: Toggles ────────────────────────────────────────
document.querySelectorAll('.toggle').forEach(toggle => {
  const key = toggle.dataset.key;
  if (key && localStorage.getItem(`toggle_${key}`) === 'on') toggle.classList.add('on');
  toggle.addEventListener('click', () => {
    toggle.classList.toggle('on');
    if (key) localStorage.setItem(`toggle_${key}`, toggle.classList.contains('on') ? 'on' : 'off');
  });
});

['game-dir', 'java-path'].forEach(id => {
  const el = document.getElementById(id);
  if (el) {
    const saved = localStorage.getItem(`setting_${id}`);
    if (saved) el.value = saved;
    el.addEventListener('change', () => localStorage.setItem(`setting_${id}`, el.value));
  }
});


// ── Toast ────────────────────────────────────────────────────
const toastEl = document.getElementById('toast');
let toastTimer;

function showToast(msg, dur = 3500) {
  toastEl.textContent = msg;
  toastEl.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('show'), dur);
}


// ── Helpers ──────────────────────────────────────────────────
function delay(ms) { return new Promise(r => setTimeout(r, ms)); }

function escapeHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function formatDownloads(n) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000)     return (n / 1_000).toFixed(1) + 'K';
  return String(n);
}
function formatBytes(b) {
  if (b >= 1024*1024) return (b / (1024*1024)).toFixed(1) + ' MB';
  if (b >= 1024)      return (b / 1024).toFixed(1) + ' KB';
  return b + ' B';
}


// ── Mods Tab ─────────────────────────────────────────────────
let modsSearchOffset = 0;
let modsSearchTotal  = 0;

const modSearchInput      = document.getElementById('mod-search-input');
const modFilterVersion    = document.getElementById('mod-filter-version');
const modFilterLoader     = document.getElementById('mod-filter-loader');
const modsSearchBtn       = document.getElementById('mods-search-btn');
const modsGrid            = document.getElementById('mods-grid');
const modsResultsWrap     = document.getElementById('mods-results-wrap');
const modsResultsTitle    = document.getElementById('mods-results-title');
const modsMoreBtn         = document.getElementById('mods-more-btn');
const installedModsList   = document.getElementById('installed-mods-list');
const modsInstalledCount  = document.getElementById('mods-installed-count');
const refreshInstalledBtn = document.getElementById('refresh-installed-btn');
const openModsFolderBtn   = document.getElementById('open-mods-folder-btn');

// Sync version filter with currently selected MC version
if (modFilterVersion) {
  const cur = localStorage.getItem('mc_version') || '1.21.4';
  const opt = modFilterVersion.querySelector(`option[value="${cur}"]`);
  if (opt) modFilterVersion.value = cur;
}

// ── Loader chips ─────────────────────────────────────────────
async function checkLoaderChips() {
  const mcVersion = modFilterVersion?.value || localStorage.getItem('mc_version') || '1.21.1';
  const gameDir   = document.getElementById('game-dir')?.value?.trim() || '';
  const result    = await window.electronAPI?.checkLoaders({ mcVersion, gameDir });
  if (!result) return;
  setLoaderChip('fabric',   result.fabric);
  setLoaderChip('forge',    result.forge);
  setLoaderChip('neoforge', result.neoforge);
}

function setLoaderChip(loader, status) {
  const chip = document.getElementById(`lchip-${loader}`);
  const ver  = document.getElementById(`lver-${loader}`);
  const btn  = document.getElementById(`lbtn-${loader}`);
  if (!chip || !ver || !btn) return;
  if (status.installed) {
    chip.classList.add('installed');
    chip.classList.remove('not-installed');
    const shortVer = (status.versionId || '').split('-').pop() || 'установлен';
    ver.textContent = shortVer;
    btn.textContent = '✓ Активен';
    btn.disabled = true;
  } else {
    chip.classList.remove('installed');
    chip.classList.add('not-installed');
    ver.textContent = 'не установлен';
    btn.textContent = 'Установить';
    btn.disabled = false;
  }
}

async function installLoader(type) {
  const mcVersion = modFilterVersion?.value || localStorage.getItem('mc_version') || '1.21.1';
  const gameDir   = document.getElementById('game-dir')?.value?.trim() || '';
  const javaPath  = document.getElementById('java-path')?.value?.trim() || '';

  ['fabric','forge','neoforge'].forEach(l => {
    const b = document.getElementById(`lbtn-${l}`);
    if (b) b.disabled = true;
  });

  const progressWrap = document.getElementById('loader-install-progress');
  progressWrap.style.display = 'flex';

  const result = await window.electronAPI?.installLoader({ type, mcVersion, gameDir, javaPath });

  progressWrap.style.display = 'none';
  document.getElementById('lip-bar').style.width = '0%';

  if (result?.success) {
    showToast(`${type.charAt(0).toUpperCase() + type.slice(1)} установлен!`);
    checkLoaderChips();
  } else {
    showToast(result?.error || `Ошибка установки ${type}`);
    ['fabric','forge','neoforge'].forEach(l => {
      const b = document.getElementById(`lbtn-${l}`);
      if (b) b.disabled = false;
    });
    checkLoaderChips();
  }
}

window.electronAPI?.onLoaderProgress(({ msg, pct }) => {
  const m = document.getElementById('lip-msg');
  const p = document.getElementById('lip-pct');
  const b = document.getElementById('lip-bar');
  if (m) m.textContent = msg;
  if (p) p.textContent = `${pct}%`;
  if (b) b.style.width = `${pct}%`;
});

document.getElementById('lbtn-fabric')?.addEventListener('click',   () => installLoader('fabric'));
document.getElementById('lbtn-forge')?.addEventListener('click',    () => installLoader('forge'));
document.getElementById('lbtn-neoforge')?.addEventListener('click', () => installLoader('neoforge'));

// Open mods tab → refresh
document.querySelector('[data-tab="mods"]')?.addEventListener('click', () => {
  checkLoaderChips();
  loadInstalledMods();
});

// ── Modrinth search ───────────────────────────────────────────
async function searchMods(reset = true) {
  if (reset) {
    modsSearchOffset = 0;
    if (modsGrid) modsGrid.innerHTML = '<div class="mods-loading"><div class="lp-spinner"></div></div>';
  }

  const query     = modSearchInput?.value?.trim() || '';
  const mcVersion = modFilterVersion?.value || '1.21.1';
  const loader    = modFilterLoader?.value  || 'all';

  if (modsResultsWrap) modsResultsWrap.style.display = 'block';
  if (modsResultsTitle) modsResultsTitle.textContent = 'Поиск...';

  const result = await window.electronAPI?.searchMods({ query, mcVersion, loader, offset: modsSearchOffset });

  if (!result?.success) {
    if (modsGrid) modsGrid.innerHTML = `<div class="mods-empty-hint">${result?.error || 'Ошибка поиска'}</div>`;
    return;
  }

  modsSearchTotal = result.total;
  if (modsResultsTitle) modsResultsTitle.textContent = `Найдено ${result.total.toLocaleString()} модов`;

  if (reset && modsGrid) modsGrid.innerHTML = '';

  if (!result.hits.length) {
    if (reset && modsGrid) modsGrid.innerHTML = '<div class="mods-empty-hint">Ничего не найдено</div>';
    if (modsMoreBtn) modsMoreBtn.style.display = 'none';
    return;
  }

  result.hits.forEach(mod => modsGrid?.appendChild(createModCard(mod)));

  modsSearchOffset += result.hits.length;
  if (modsMoreBtn) modsMoreBtn.style.display = modsSearchOffset < modsSearchTotal ? 'block' : 'none';
}

function createModCard(mod) {
  const card = document.createElement('div');
  card.className = 'mod-card';

  const iconHtml = mod.icon_url
    ? `<img class="mod-card-icon" src="${mod.icon_url}" alt="" loading="lazy"
         onerror="this.style.display='none';this.nextElementSibling.style.display='flex'">`
    : '';
  const fallbackStyle = mod.icon_url ? 'display:none' : '';

  card.innerHTML = `
    <div class="mod-card-top">
      <div class="mod-card-icon-wrap">
        ${iconHtml}
        <div class="mod-card-icon-fallback" style="${fallbackStyle}">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.35">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          </svg>
        </div>
      </div>
      <div class="mod-card-info">
        <div class="mod-card-name">${escapeHtml(mod.title)}</div>
        <div class="mod-card-desc">${escapeHtml((mod.description || '').slice(0, 90))}${(mod.description || '').length > 90 ? '…' : ''}</div>
      </div>
    </div>
    <div class="mod-card-meta">
      <span class="mod-card-dl">↓ ${formatDownloads(mod.downloads || 0)}</span>
      ${(mod.categories || []).slice(0, 2).map(c => `<span class="mod-cat">${escapeHtml(c)}</span>`).join('')}
    </div>
    <button class="mod-card-install-btn" data-id="${mod.project_id}">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
      </svg>
      Скачать
    </button>
  `;

  card.querySelector('.mod-card-install-btn').addEventListener('click', async (e) => {
    const btn       = e.currentTarget;
    const projectId = btn.dataset.id;
    const mcVersion = modFilterVersion?.value || '1.21.1';
    const loader    = modFilterLoader?.value  || 'all';
    const gameDir   = document.getElementById('game-dir')?.value?.trim() || '';

    btn.disabled = true;
    btn.innerHTML = '<div class="lp-spinner" style="width:13px;height:13px;border-width:2px"></div>';

    const result = await window.electronAPI?.installMod({ projectId, mcVersion, loader, gameDir });

    if (result?.success) {
      btn.innerHTML = '✓ Установлен';
      btn.style.cssText = 'background:rgba(74,222,128,0.1);border-color:rgba(74,222,128,0.3);color:#4ade80;';
      showToast(`${result.fileName} установлен`);
      loadInstalledMods();
    } else {
      btn.disabled = false;
      btn.innerHTML = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Скачать`;
      showToast(result?.error || 'Ошибка установки');
    }
  });

  return card;
}

modsSearchBtn?.addEventListener('click', () => searchMods(true));
modSearchInput?.addEventListener('keydown', e => { if (e.key === 'Enter') searchMods(true); });
modsMoreBtn?.addEventListener('click', () => searchMods(false));

// ── Installed mods ────────────────────────────────────────────
async function loadInstalledMods() {
  const gameDir = document.getElementById('game-dir')?.value?.trim() || '';
  const result  = await window.electronAPI?.getInstalledMods({ gameDir });

  if (!result?.success || !result.mods.length) {
    if (installedModsList) installedModsList.innerHTML = '<div class="mods-empty-hint">Папка модов пуста — установи моды через поиск выше</div>';
    if (modsInstalledCount) modsInstalledCount.textContent = '0';
    return;
  }

  if (modsInstalledCount) modsInstalledCount.textContent = result.mods.length;
  if (installedModsList) {
    installedModsList.innerHTML = result.mods.map(mod => `
      <div class="installed-mod-row">
        <div class="installed-mod-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          </svg>
        </div>
        <div class="installed-mod-name" title="${escapeHtml(mod.name)}">${escapeHtml(mod.name)}</div>
        <div class="installed-mod-size">${formatBytes(mod.size)}</div>
        <button class="installed-mod-delete" data-name="${escapeHtml(mod.name)}" title="Удалить">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
            <path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
          </svg>
        </button>
      </div>
    `).join('');

    installedModsList.querySelectorAll('.installed-mod-delete').forEach(btn => {
      btn.addEventListener('click', async () => {
        const name    = btn.dataset.name;
        const gameDir2 = document.getElementById('game-dir')?.value?.trim() || '';
        const r = await window.electronAPI?.deleteMod({ filename: name, gameDir: gameDir2 });
        if (r?.success) { showToast(`${name} удалён`); loadInstalledMods(); }
        else showToast(r?.error || 'Ошибка удаления');
      });
    });
  }
}

refreshInstalledBtn?.addEventListener('click', loadInstalledMods);
openModsFolderBtn?.addEventListener('click', () => {
  const gameDir = document.getElementById('game-dir')?.value?.trim() || '';
  window.electronAPI?.openModsFolder({ gameDir });
});
