const savedAccent = localStorage.getItem('biw:accent');
const validAccents = ['#f5c84b', '#7aa2ff', '#d87a4a', '#e6e8ee'];
const accent = validAccents.includes(savedAccent) ? savedAccent : '#f5c84b';

const state = {
  view: 'home',
  modFilter: 'all',
  modSearch: '',
  ram: Number(localStorage.getItem('biw:ram') || 6),
  fps: Number(localStorage.getItem('biw:fps') || 240),
  chunks: Number(localStorage.getItem('biw:chunks') || 12),
  version: localStorage.getItem('biw:version') || '1.20.6',
  accent,
  account: null,
  hasMicrosoftClientId: false,
  configPath: '',
  settings: null,
  versions: [],
  launchLog: [],
  compact: localStorage.getItem('biw:compact') === 'true',
  launching: false,
  modules: JSON.parse(localStorage.getItem('biw:modules') || '{}'),
  options: JSON.parse(localStorage.getItem('biw:options') || '{}'),
  offlineMode: localStorage.getItem('biw:offlineMode') === 'true',
  offlineName: localStorage.getItem('biw:offlineName') || 'Player'
};

// Defaults for the expanded Settings page (sliders + segmented controls).
const optionDefaults = {
  render: 12,
  maxFps: 240,
  gamma: 100,
  graphics: 'fancy',
  hudScale: 100,
  zoom: 4
};

// Suffix/label for each slider readout element id.
const sliderReadouts = {
  render: { id: 'renderReadout', suffix: (v) => `${v} chunks` },
  maxFps: { id: 'maxFpsReadout', suffix: (v) => `${v} FPS` },
  gamma: { id: 'gammaReadout', suffix: (v) => `${v}%` },
  hudScale: { id: 'hudScaleReadout', suffix: (v) => `${v}%` },
  zoom: { id: 'zoomReadout', suffix: (v) => `${v}x` }
};

const moduleData = [
  {
    id: 'hitcolor',
    name: 'Hit Color',
    type: 'pvp',
    icon: 'swords',
    desc: 'Clean hit feedback with configurable damage tones.',
    version: '2.4.1',
    active: true
  },
  {
    id: 'keystrokes',
    name: 'Keystrokes',
    type: 'pvp',
    icon: 'keyboard',
    desc: 'Low-latency inputs with CPS and sprint states.',
    version: '1.9.8',
    active: true
  },
  {
    id: 'bridge',
    name: 'Bridge Assist HUD',
    type: 'pvp',
    icon: 'waypoints',
    desc: 'Angle, block count, and timing overlay for Bedwars.',
    version: '1.3.0',
    active: true
  },
  {
    id: 'nametags',
    name: 'Smart Nametags',
    type: 'visual',
    icon: 'scan-text',
    desc: 'Readable tags with party, guild, and threat states.',
    version: '3.1.2',
    active: true
  },
  {
    id: 'minimap',
    name: 'Minimap Pro',
    type: 'utility',
    icon: 'map',
    desc: 'Compact map, waypoints, death markers, and chunk grid.',
    version: '4.0.6',
    active: false
  },
  {
    id: 'replay',
    name: 'Replay Tools',
    type: 'utility',
    icon: 'video',
    desc: 'Clip markers, timeline scrubbing, and export presets.',
    version: '0.8.9',
    active: false
  },
  {
    id: 'sodium',
    name: 'Sodium Stack',
    type: 'performance',
    icon: 'cpu',
    desc: 'Renderer path tuned for stable frames and fast input.',
    version: '5.2.0',
    active: true
  },
  {
    id: 'entitycull',
    name: 'Entity Culling',
    type: 'performance',
    icon: 'scan-eye',
    desc: 'Skips hidden entities and particles outside view.',
    version: '1.7.3',
    active: true
  },
  {
    id: 'motionblur',
    name: 'Cinematic Motion',
    type: 'visual',
    icon: 'aperture',
    desc: 'Optional smooth camera pass for clips and replays.',
    version: '2.0.4',
    active: false
  }
];

const quickModuleIds = ['hitcolor', 'keystrokes', 'bridge'];
const performanceModuleIds = ['sodium', 'entitycull', 'nametags'];

const servers = [
  {
    name: 'Hypixel Network',
    mode: 'Bedwars, Skywars, Duels',
    players: '68.4k online',
    ping: 42,
    bars: [18, 26, 35, 44, 38, 31, 48, 41, 32, 45, 52, 40, 34, 46, 36, 29, 42, 50]
  },
  {
    name: 'Minemen Club',
    mode: 'Practice PvP',
    players: '4.8k online',
    ping: 55,
    bars: [22, 30, 33, 29, 36, 44, 39, 41, 34, 31, 35, 43, 47, 38, 29, 40, 36, 45]
  },
  {
    name: 'CubeCraft',
    mode: 'Skyblock, Survival Games',
    players: '12.2k online',
    ping: 61,
    bars: [15, 20, 26, 31, 35, 29, 33, 39, 42, 37, 34, 44, 38, 32, 28, 30, 41, 36]
  },
  {
    name: 'PvP Legacy',
    mode: 'Kit editor, duels',
    players: '2.1k online',
    ping: 72,
    bars: [12, 18, 21, 24, 30, 27, 31, 36, 29, 33, 38, 34, 30, 26, 32, 35, 28, 31]
  },
  {
    name: 'The Hive',
    mode: 'Minigames',
    players: '28.9k online',
    ping: 49,
    bars: [20, 27, 34, 41, 36, 40, 47, 39, 33, 37, 44, 51, 45, 39, 35, 43, 48, 42]
  },
  {
    name: 'BIW Labs',
    mode: 'Private testing',
    players: '128 online',
    ping: 18,
    bars: [34, 42, 48, 55, 51, 59, 62, 58, 53, 61, 64, 57, 52, 60, 56, 63, 59, 66]
  }
];

const launchSteps = [
  'Checking profile',
  'Loading modules',
  'Optimizing renderer',
  'Preparing assets',
  'Starting Minecraft'
];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function iconize() {
  if (window.lucide) {
    window.lucide.createIcons({
      attrs: {
        'aria-hidden': 'true'
      }
    });
  }
}

function persist() {
  localStorage.setItem('biw:ram', String(state.ram));
  localStorage.setItem('biw:fps', String(state.fps));
  localStorage.setItem('biw:chunks', String(state.chunks));
  localStorage.setItem('biw:version', state.version);
  localStorage.setItem('biw:accent', state.accent);
  localStorage.setItem('biw:compact', String(state.compact));
  localStorage.setItem('biw:modules', JSON.stringify(state.modules));
  localStorage.setItem('biw:options', JSON.stringify(state.options));
  localStorage.setItem('biw:offlineMode', String(state.offlineMode));
  localStorage.setItem('biw:offlineName', state.offlineName);
}

function optionValue(key) {
  return state.options[key] ?? optionDefaults[key];
}

function saveClientOption(key, value) {
  window.biwClient?.saveSettings?.({
    clientOptions: {
      [key]: value
    }
  });
}

function setOption(key, value) {
  state.options[key] = value;
  persist();
  saveClientOption(key, value);
}

function initialModuleState(mod) {
  return state.modules[mod.id] ?? mod.active;
}

function moduleIsActive(id) {
  const mod = moduleData.find((item) => item.id === id);
  return mod ? initialModuleState(mod) : false;
}

function setModuleActive(id, active) {
  state.modules[id] = active;
  persist();
  renderModuleAreas();
  updateOptimization();
  updateLauncherSummary();
}

function showToast(title, body, icon = 'badge-check') {
  const stack = $('#toastStack');
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.innerHTML = `
    <i data-lucide="${icon}"></i>
    <div>
      <strong>${title}</strong>
      <span>${body}</span>
    </div>
  `;
  stack.appendChild(toast);
  iconize();

  window.setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(8px)';
    toast.style.transition = 'opacity 180ms ease, transform 180ms ease';
    window.setTimeout(() => toast.remove(), 220);
  }, 3200);
}

function navigate(view) {
  state.view = view;
  $$('.view').forEach((item) => item.classList.toggle('is-active', item.id === view));
  $$('.nav-item').forEach((item) => item.classList.toggle('is-active', item.dataset.view === view));
}

function moduleToggleTemplate(mod, compact = false) {
  const checked = initialModuleState(mod) ? 'checked' : '';
  const subtitle = compact ? mod.type : `${mod.type} / v${mod.version}`;

  return `
    <div class="toggle-row">
      <div class="module-icon"><i data-lucide="${mod.icon}"></i></div>
      <div>
        <strong>${mod.name}</strong>
        <span>${subtitle}</span>
      </div>
      <label class="switch" aria-label="${mod.name}">
        <input type="checkbox" data-module-toggle="${mod.id}" ${checked} />
        <span></span>
      </label>
    </div>
  `;
}

function renderQuickModules() {
  const target = $('#quickModules');
  target.innerHTML = quickModuleIds
    .map((id) => moduleData.find((item) => item.id === id))
    .filter(Boolean)
    .map((mod) => moduleToggleTemplate(mod, true))
    .join('');
}

function renderPerformanceModules() {
  const target = $('#performanceModules');
  target.innerHTML = performanceModuleIds
    .map((id) => moduleData.find((item) => item.id === id))
    .filter(Boolean)
    .map((mod) => moduleToggleTemplate(mod))
    .join('');
}

function renderMods() {
  const grid = $('#modGrid');
  const search = state.modSearch.trim().toLowerCase();
  const mods = moduleData.filter((mod) => {
    const matchesFilter = state.modFilter === 'all' || mod.type === state.modFilter;
    const matchesSearch = !search || `${mod.name} ${mod.desc} ${mod.type}`.toLowerCase().includes(search);
    return matchesFilter && matchesSearch;
  });

  grid.innerHTML = mods
    .map((mod) => {
      const active = initialModuleState(mod);
      return `
        <article class="mod-card">
          <div class="mod-card__head">
            <div class="module-icon"><i data-lucide="${mod.icon}"></i></div>
            <label class="switch" aria-label="${mod.name}">
              <input type="checkbox" data-module-toggle="${mod.id}" ${active ? 'checked' : ''} />
              <span></span>
            </label>
          </div>
          <div>
            <h3>${mod.name}</h3>
            <p>${mod.desc}</p>
          </div>
          <div class="mod-meta">
            <span class="mod-badge">${mod.type}</span>
            <span>v${mod.version}</span>
          </div>
        </article>
      `;
    })
    .join('');

  if (!mods.length) {
    grid.innerHTML = `
      <article class="mod-card">
        <div class="mod-card__head">
          <div class="module-icon"><i data-lucide="search-x"></i></div>
        </div>
        <div>
          <h3>No modules</h3>
          <p>Try a different search or filter.</p>
        </div>
        <div class="mod-meta">
          <span class="mod-badge">empty</span>
          <span>0 results</span>
        </div>
      </article>
    `;
  }
}

function renderModuleAreas() {
  renderQuickModules();
  renderPerformanceModules();
  renderMods();
  bindModuleToggles();
  updateLauncherSummary();
  iconize();
}

function renderServers() {
  const grid = $('#serverGrid');
  grid.innerHTML = servers
    .map(
      (server) => `
        <article class="server-card">
          <div class="server-card__top">
            <div>
              <h3>${server.name}</h3>
              <p>${server.mode}</p>
            </div>
            <span class="server-ping">${server.ping} ms</span>
          </div>
          <div class="server-bars" aria-hidden="true">
            ${server.bars.map((height) => `<span style="height: ${height}px"></span>`).join('')}
          </div>
          <div class="profile-summary profile-summary--compact">
            <div>
              <span>Players</span>
              <strong>${server.players}</strong>
            </div>
            <div>
              <span>Status</span>
              <strong>Online</strong>
            </div>
          </div>
        </article>
      `
    )
    .join('');
}

function bindModuleToggles() {
  $$('[data-module-toggle]').forEach((input) => {
    input.addEventListener('change', (event) => {
      const id = event.currentTarget.dataset.moduleToggle;
      const active = event.currentTarget.checked;
      const mod = moduleData.find((item) => item.id === id);
      setModuleActive(id, active);
      showToast(active ? 'Module enabled' : 'Module disabled', mod?.name || 'Module', active ? 'toggle-right' : 'toggle-left');
    });
  });
}

function updateLauncherSummary() {
  const activeModules = moduleData.filter((mod) => moduleIsActive(mod.id)).length;
  const versionLabel = `${state.version || '1.20.6'} Vanilla`;
  const signedIn = Boolean(state.account?.signedIn);

  $('#selectedVersionLabel').textContent = versionLabel;
  $('#moduleCountLabel').textContent = `${activeModules} active`;
  $('#accountHealthLabel').textContent = state.offlineMode
    ? `${state.offlineName} (offline)`
    : signedIn
      ? state.account.profile.name
      : 'Offline';
  $('#loginHeroBtn').innerHTML = signedIn ? '<i data-lucide="log-out"></i> Sign out' : '<i data-lucide="log-in"></i> Sign in';
  $('#launchBtn').disabled = state.launching;
  $('#launchBtn').innerHTML = state.launching
    ? '<i data-lucide="loader"></i> Launching'
    : state.offlineMode
      ? '<i data-lucide="play"></i> Launch (Offline)'
      : '<i data-lucide="play"></i> Launch';
  iconize();
}

function updateControls() {
  $('#ramSlider').value = state.ram;
  $('#fpsSlider').value = state.fps;
  $('#chunkSlider').value = state.chunks;
  $('#ramReadout').textContent = `${state.ram} GB`;
  $('#fpsReadout').textContent = `${state.fps} FPS`;
  $('#chunkReadout').textContent = `${state.chunks} chunks`;
  $('#memoryValue').textContent = `${state.ram} GB`;
  document.documentElement.style.setProperty('--accent', state.accent);
  document.body.classList.toggle('compact', state.compact);
  $('#densitySwitch').checked = state.compact;
  $$('.swatch').forEach((button) => button.classList.toggle('is-active', button.dataset.accent === state.accent));
  if ($('#versionSelect')) {
    $('#versionSelect').value = state.version;
  }
  updateOptimization();
  updateLauncherSummary();
}

function syncSettingsControls() {
  $$('[data-setting]').forEach((input) => {
    const key = input.dataset.setting;
    if (input.type === 'checkbox') {
      const stored = state.options[key];
      if (typeof stored === 'boolean') input.checked = stored;
    } else if (input.type === 'range') {
      input.value = optionValue(key);
      updateSliderReadout(key);
    }
  });

  $$('.seg-control').forEach((group) => {
    const key = group.dataset.setting;
    const current = optionValue(key);
    group.querySelectorAll('.seg-btn').forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.value === current);
    });
  });

  document.body.classList.toggle('no-effects', state.options.bgEffects === false);
}

function updateOptimization() {
  const activeModules = moduleData.filter((mod) => moduleIsActive(mod.id)).length;
  const ramScore = Math.min(22, state.ram * 1.4);
  const fpsScore = Math.min(24, state.fps / 12);
  const chunkPenalty = Math.max(0, state.chunks - 16) * 0.6;
  const moduleScore = activeModules * 5.6;
  const score = Math.max(48, Math.min(99, Math.round(42 + ramScore + fpsScore + moduleScore - chunkPenalty)));

  $('#optimizationScore').textContent = `${score}%`;
  $('#optimizationBar').style.width = `${score}%`;
}

function setLaunchStatus(message = 'Ready', percent = 0) {
  const normalized = typeof percent === 'number' ? Math.max(0, Math.min(100, percent)) : null;
  $('#launchStatusText').textContent = message;
  $('#launchPercentText').textContent = normalized === null ? '--' : `${normalized}%`;
  if (normalized !== null) {
    $('#overlayProgress').style.width = `${normalized}%`;
    $('#launchProgress span').style.width = `${normalized}%`;
  }
}

function appendLaunchLog(message) {
  const log = $('#launchLog');
  const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  state.launchLog = [`${time}  ${message}`, ...state.launchLog].slice(0, 5);
  log.replaceChildren(
    ...state.launchLog.map((line) => {
      const row = document.createElement('span');
      row.textContent = line;
      return row;
    })
  );
}

function setLaunchOverlay(active, message = 'Preparing runtime', percent = 0) {
  const overlay = $('#launchOverlay');
  const stepText = $('#launchStep');
  overlay.classList.toggle('is-active', active);
  overlay.setAttribute('aria-hidden', active ? 'false' : 'true');
  stepText.textContent = message;
  setLaunchStatus(message, percent);
}

function setLaunchProgress(message, percent) {
  $('#launchStep').textContent = message;
  setLaunchStatus(message, percent);
}

async function loginAccount() {
  if (!state.hasMicrosoftClientId) {
    navigate('settings');
    showToast('Microsoft setup needed', 'Add microsoftClientId in config.local.json first.', 'circle-alert');
    throw new Error('Missing Microsoft client id.');
  }

  setLaunchOverlay(true, 'Opening Microsoft sign-in window', 8);
  appendLaunchLog('Opening official Microsoft sign-in');
  try {
    const account = await window.biwClient?.loginMicrosoft?.();
    state.account = account;
    updateAccountUI();
    showToast('Signed in', account?.profile?.name || 'Minecraft profile loaded.', 'badge-check');
    return account;
  } catch (error) {
    showToast('Sign-in failed', error.message || String(error), 'circle-alert');
    throw error;
  } finally {
    setLaunchOverlay(false);
  }
}

async function logoutAccount() {
  const account = await window.biwClient?.logout?.();
  state.account = account;
  updateAccountUI();
  updateLauncherSummary();
  showToast('Signed out', 'Local session was removed.', 'log-out');
}

async function runLaunch() {
  if (state.launching) return;
  state.launching = true;
  updateLauncherSummary();

  try {
    state.version = $('#versionSelect').value || state.version || '1.20.6';
    persist();

    // Offline / dev mode: launch directly with a local name, no auth.
    if (state.offlineMode) {
      await window.biwClient?.saveSettings?.({
        ramGb: state.ram,
        versionId: state.version,
        clientOptions: state.options
      });
      setLaunchOverlay(true, 'Preparing Minecraft (offline)', 3);
      appendLaunchLog(`Offline launch requested for ${state.version} as ${state.offlineName}`);
      const result = await window.biwClient?.launchOfflineMinecraft?.({
        username: state.offlineName,
        ramGb: state.ram,
        versionId: state.version
      });
      showToast('Minecraft started', `${result.profile.name} / ${result.versionId} (offline)`, 'play');
      setLaunchProgress('Minecraft started', 100);
      window.setTimeout(() => setLaunchOverlay(false), 900);
      return;
    }

    let account = state.account;
    if (!account?.signedIn) {
      account = await loginAccount();
    }

    await window.biwClient?.saveSettings?.({
      ramGb: state.ram,
      versionId: state.version,
      clientOptions: state.options
    });

    setLaunchOverlay(true, 'Preparing Minecraft', 3);
    appendLaunchLog(`Launch requested for ${state.version}`);
    const result = await window.biwClient?.launchMinecraft?.({
      ramGb: state.ram,
      versionId: state.version
    });

    showToast('Minecraft started', `${result.profile.name} / ${result.versionId}`, 'play');
    setLaunchProgress('Minecraft started', 100);
    window.setTimeout(() => setLaunchOverlay(false), 900);
  } catch (error) {
    setLaunchOverlay(false);
    appendLaunchLog(error.message || String(error));
    showToast('Launch failed', error.message || String(error), 'triangle-alert');
  } finally {
    state.launching = false;
    updateLauncherSummary();
  }
}

function handleMinecraftStatus(payload) {
  if (!payload) return;
  const message = payload.message || payload.stage || 'Working';
  setLaunchProgress(message, payload.percent);
  appendLaunchLog(message);

  if (payload.stage === 'running') {
    showToast('Minecraft window ready', message, 'monitor');
  }

  if (payload.stage === 'exit') {
    showToast('Minecraft exited', message, payload.code === 0 ? 'badge-check' : 'circle-alert');
  }

  if (payload.stage === 'error') {
    setLaunchOverlay(false);
    showToast('Minecraft error', message, 'triangle-alert');
  }
}

function drawFpsChart() {
  const canvas = $('#fpsChart');
  const ctx = canvas.getContext('2d');
  const values = Array.from({ length: 38 }, (_, index) => 170 + Math.sin(index / 2.8) * 28 + Math.random() * 22);

  function resize() {
    const rect = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    canvas.width = Math.max(1, Math.floor(rect.width * ratio));
    canvas.height = Math.max(1, Math.floor(rect.height * ratio));
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function render() {
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    const max = 260;
    const min = 80;
    ctx.clearRect(0, 0, width, height);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.07)';
    ctx.lineWidth = 1;
    for (let i = 1; i < 4; i += 1) {
      const y = (height / 4) * i;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(width, y);
      ctx.stroke();
    }

    const step = width / (values.length - 1);
    const points = values.map((value, index) => ({
      x: index * step,
      y: height - ((value - min) / (max - min)) * height
    }));

    const gradient = ctx.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, 'rgba(245, 200, 75, 0.34)');
    gradient.addColorStop(1, 'rgba(255, 126, 61, 0.02)');

    ctx.beginPath();
    ctx.moveTo(points[0].x, height);
    points.forEach((point) => ctx.lineTo(point.x, point.y));
    ctx.lineTo(points[points.length - 1].x, height);
    ctx.closePath();
    ctx.fillStyle = gradient;
    ctx.fill();

    ctx.beginPath();
    points.forEach((point, index) => {
      if (index === 0) ctx.moveTo(point.x, point.y);
      else ctx.lineTo(point.x, point.y);
    });
    ctx.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#f5c84b';
    ctx.lineWidth = 2;
    ctx.stroke();
  }

  resize();
  window.addEventListener('resize', () => {
    resize();
    render();
  });

  window.setInterval(() => {
    values.shift();
    values.push(170 + Math.sin(Date.now() / 900) * 24 + Math.random() * 34);
    render();
  }, 900);

  render();
}

function randomizeCosmetic() {
  const palettes = [
    ['#f5c84b', '#6f5514', '#7aa2ff', '#23304f'],
    ['#d87a4a', '#552615', '#f5c84b', '#6f5514'],
    ['#e6e8ee', '#525a66', '#7aa2ff', '#23304f'],
    ['#b8bec8', '#232832', '#f5c84b', '#6f5514']
  ];
  const palette = palettes[Math.floor(Math.random() * palettes.length)];
  $('.skin-body').style.background = `linear-gradient(135deg, ${palette[0]}, ${palette[1]})`;
  $$('.skin-arm').forEach((part) => {
    part.style.background = `linear-gradient(135deg, ${palette[2]}, ${palette[3]})`;
  });
  showToast('Cosmetic set applied', 'Preview updated.', 'sparkles');
}

function bindEvents() {
  $$('.nav-item').forEach((item) => item.addEventListener('click', () => navigate(item.dataset.view)));
  $$('[data-view-link]').forEach((item) => item.addEventListener('click', () => navigate(item.dataset.viewLink)));

  $('#minimizeBtn').addEventListener('click', () => window.biwClient?.minimize());
  $('#maximizeBtn').addEventListener('click', () => window.biwClient?.maximize());
  $('#closeBtn').addEventListener('click', () => window.biwClient?.close());

  $('#launchBtn').addEventListener('click', runLaunch);
  $('#loginHeroBtn').addEventListener('click', async () => {
    if (state.account?.signedIn) await logoutAccount();
    else await loginAccount();
  });
  $('#accountBtn').addEventListener('click', async () => {
    if (state.account?.signedIn) await logoutAccount();
    else await loginAccount();
  });
  $('#joinServerBtn').addEventListener('click', () => showToast('Queue joined', 'Hypixel Network selected.', 'plug-zap'));
  $('#quickBoostBtn').addEventListener('click', () => {
    state.fps = 300;
    state.ram = Math.max(state.ram, 8);
    state.chunks = Math.min(state.chunks, 12);
    state.modules.sodium = true;
    state.modules.entitycull = true;
    state.modules.nametags = true;
    persist();
    updateControls();
    renderModuleAreas();
    showToast('Boost applied', 'Performance profile updated.', 'rocket');
  });

  $('#autoTuneBtn').addEventListener('click', () => {
    state.ram = 8;
    state.fps = 300;
    state.chunks = 10;
    state.modules.sodium = true;
    state.modules.entitycull = true;
    persist();
    updateControls();
    renderModuleAreas();
    showToast('Auto tune complete', 'Settings balanced for PvP.', 'wand-sparkles');
  });

  $('#newProfileBtn').addEventListener('click', () => showToast('Profile draft ready', 'New profile slot created.', 'plus'));
  $('#addServerBtn').addEventListener('click', () => showToast('Server form ready', 'Add-server flow can be wired next.', 'server'));
  $('#randomizeCosmeticBtn').addEventListener('click', randomizeCosmetic);

  $('#ramSlider').addEventListener('input', (event) => {
    state.ram = Number(event.target.value);
    persist();
    updateControls();
  });
  $('#ramSlider').addEventListener('change', () => {
    window.biwClient?.saveSettings?.({ ramGb: state.ram });
  });
  $('#fpsSlider').addEventListener('input', (event) => {
    state.fps = Number(event.target.value);
    persist();
    updateControls();
  });
  $('#chunkSlider').addEventListener('input', (event) => {
    state.chunks = Number(event.target.value);
    persist();
    updateControls();
  });
  $('#versionSelect').addEventListener('change', (event) => {
    state.version = event.target.value;
    persist();
    updateControls();
    window.biwClient?.saveSettings?.({ versionId: state.version });
    showToast('Version selected', state.version, 'box');
  });

  $('#modSearch').addEventListener('input', (event) => {
    state.modSearch = event.target.value;
    renderModuleAreas();
  });

  $$('.chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      state.modFilter = chip.dataset.filter;
      $$('.chip').forEach((item) => item.classList.toggle('is-active', item === chip));
      renderModuleAreas();
    });
  });

  $$('.profile-tile').forEach((tile) => {
    tile.addEventListener('click', () => {
      $$('.profile-tile').forEach((item) => item.classList.toggle('is-selected', item === tile));
      showToast('Profile selected', $('h3', tile).textContent, 'layers-3');
    });
  });

  $$('.cosmetic-tile').forEach((tile) => {
    tile.addEventListener('click', () => {
      $$('.cosmetic-tile').forEach((item) => item.classList.toggle('is-selected', item === tile));
      showToast('Cosmetic selected', tile.textContent.trim(), 'sparkles');
    });
  });

  $('#swatches').addEventListener('click', (event) => {
    const button = event.target.closest('.swatch');
    if (!button) return;
    state.accent = button.dataset.accent;
    persist();
    updateControls();
    showToast('Accent updated', state.accent, 'palette');
  });

  $('#densitySwitch').addEventListener('change', (event) => {
    state.compact = event.target.checked;
    persist();
    updateControls();
  });

  $('#selectDirBtn').addEventListener('click', async () => {
    const directory = await window.biwClient?.selectGameDirectory?.();
    if (directory) {
      $('#gameDirectory').textContent = directory;
      if (state.settings) state.settings.minecraftDirectory = directory;
      showToast('Directory selected', directory, 'folder-open');
    }
  });

  bindSettings();
}

// Wire up every data-setting control on the expanded Settings page.
function bindSettings() {
  syncSettingsControls();

  // Toggles (checkboxes)
  $$('[data-setting]').forEach((input) => {
    if (input.type !== 'checkbox') return;
    const key = input.dataset.setting;

    input.addEventListener('change', () => {
      setOption(key, input.checked);
      if (key === 'bgEffects') {
        document.body.classList.toggle('no-effects', !input.checked);
      }
      showToast(input.checked ? 'Enabled' : 'Disabled', prettyLabel(key), input.checked ? 'check' : 'x');
    });
  });

  // Sliders (ranges)
  $$('input[type="range"][data-setting]').forEach((slider) => {
    const key = slider.dataset.setting;
    slider.addEventListener('input', () => {
      setOption(key, Number(slider.value));
      updateSliderReadout(key);
    });
  });

  // Segmented controls
  $$('.seg-control').forEach((group) => {
    const key = group.dataset.setting;
    group.querySelectorAll('.seg-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        group.querySelectorAll('.seg-btn').forEach((b) => b.classList.toggle('is-active', b === btn));
        setOption(key, btn.dataset.value);
        showToast('Updated', `${prettyLabel(key)}: ${btn.textContent.trim()}`, 'settings-2');
      });
    });
  });

  // Settings search filter
  const search = $('#settingsSearch');
  if (search) {
    search.addEventListener('input', () => filterSettings(search.value.trim().toLowerCase()));
  }

  // Offline / dev mode controls
  const offlineSwitch = $('#offlineModeSwitch');
  const offlineName = $('#offlineNameInput');
  if (offlineSwitch) {
    offlineSwitch.checked = state.offlineMode;
    offlineSwitch.addEventListener('change', () => {
      state.offlineMode = offlineSwitch.checked;
      persist();
      updateLauncherSummary();
      showToast(
        state.offlineMode ? 'Offline mode on' : 'Offline mode off',
        state.offlineMode ? 'Launch uses a local name, no sign-in.' : 'Launch uses your Microsoft account.',
        'flask-conical'
      );
    });
  }
  if (offlineName) {
    offlineName.value = state.offlineName;
    offlineName.addEventListener('input', () => {
      state.offlineName = offlineName.value.replace(/[^A-Za-z0-9_]/g, '').slice(0, 16);
      offlineName.value = state.offlineName;
      persist();
      updateLauncherSummary();
    });
  }
}

function updateSliderReadout(key) {
  const config = sliderReadouts[key];
  if (!config) return;
  const el = document.getElementById(config.id);
  if (el) el.textContent = config.suffix(optionValue(key));
}

function prettyLabel(key) {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (c) => c.toUpperCase())
    .trim();
}

function filterSettings(query) {
  $$('.settings-group').forEach((group) => {
    let visible = 0;
    group.querySelectorAll('.settings-row').forEach((row) => {
      const text = row.textContent.toLowerCase();
      const match = !query || text.includes(query);
      row.classList.toggle('is-hidden', !match);
      if (match) visible += 1;
    });
    group.classList.toggle('is-empty', visible === 0);
  });
}

function fallbackVersions() {
  return ['1.20.6', '1.20.4', '1.19.4', '1.18.2', '1.17.1', '1.16.5', '1.12.2', '1.8.9'].map((id) => ({
    id,
    type: 'release'
  }));
}

function applyVersionOptions() {
  const select = $('#versionSelect');
  const versions = state.versions.length ? state.versions : fallbackVersions();
  const unique = new Map(versions.map((version) => [version.id, version]));

  if (state.version && !unique.has(state.version)) {
    unique.set(state.version, { id: state.version, type: 'release' });
  }

  select.replaceChildren(
    ...[...unique.values()].map((version) => {
      const option = document.createElement('option');
      option.value = version.id;
      option.textContent = version.id;
      return option;
    })
  );
  select.value = state.version || [...unique.keys()][0] || '1.20.6';
  state.version = select.value;
  persist();
  updateLauncherSummary();
}

async function loadMinecraftVersions() {
  try {
    const data = await window.biwClient?.getMinecraftVersions?.();
    state.versions = Array.isArray(data?.versions) ? data.versions : fallbackVersions();
    if (!state.version && data?.latest?.release) {
      state.version = data.latest.release;
    }
  } catch (error) {
    state.versions = fallbackVersions();
    appendLaunchLog(`Version list fallback: ${error.message || error}`);
  }

  applyVersionOptions();
  updateControls();
}

async function setAppMeta() {
  const meta = await window.biwClient?.getMeta?.();
  if (meta?.version) {
    $('#appVersion').textContent = `v${meta.version} / ${meta.platform}`;
  }
  state.hasMicrosoftClientId = Boolean(meta?.hasMicrosoftClientId);
  state.configPath = meta?.configPath || '';
  $('#clientIdStatus').textContent = state.hasMicrosoftClientId
    ? 'Ready: official Microsoft login is enabled'
    : 'Missing: add microsoftClientId before login';
  $('#configPath').textContent = state.configPath || 'config.local.json';
  $('#configPathInline').textContent = state.configPath
    ? `Create ${state.configPath} and add microsoftClientId.`
    : 'Create config.local.json and add microsoftClientId.';
  $('#microsoftSetupBanner').hidden = state.hasMicrosoftClientId;
}

function updateAccountUI() {
  const profile = state.account?.profile;
  $('#accountName').textContent = profile?.name || 'Not signed in';
  $('#accountStatus').textContent = profile ? 'Microsoft account linked' : 'Microsoft account required';
  $('.avatar--player').textContent = profile?.name?.slice(0, 1).toUpperCase() || 'B';
  $('#accountBtn').innerHTML = profile ? '<i data-lucide="log-out"></i>' : '<i data-lucide="log-in"></i>';
  $('#accountBtn').title = profile ? 'Sign out' : 'Microsoft sign in';
  updateLauncherSummary();
  iconize();
}

async function refreshAccount() {
  const account = await window.biwClient?.getAccount?.();
  state.account = account || null;
  state.settings = account?.settings || null;
  if (account?.settings?.minecraftDirectory) {
    $('#gameDirectory').textContent = account.settings.minecraftDirectory;
  }
  if (account?.settings?.version) {
    state.version = account.settings.version;
    persist();
  }
  if (account?.settings?.maxMemoryMb) {
    state.ram = Math.round(account.settings.maxMemoryMb / 1024);
    updateControls();
  }
  if (account?.settings?.clientOptions) {
    state.options = {
      ...state.options,
      ...account.settings.clientOptions
    };
    persist();
    syncSettingsControls();
  }
  updateAccountUI();
}

async function init() {
  renderServers();
  renderModuleAreas();
  updateControls();
  bindEvents();
  iconize();
  window.biwClient?.onMinecraftStatus?.(handleMinecraftStatus);
  await setAppMeta();
  await refreshAccount();
  await loadMinecraftVersions();
  drawFpsChart();
}

document.addEventListener('DOMContentLoaded', init);
