const path = require('node:path');
const dns = require('node:dns');
const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const { getMicrosoftClientId, getMirror, LOCAL_CONFIG_PATH } = require('./config');
const { AccountManager } = require('./accountManager');
const { getMinecraftVersions, launchMinecraft } = require('./launcher/minecraftLauncher');
const log = require('./logger');

// Many networks (and some ISPs) advertise broken IPv6 routes, which makes
// Mojang/Microsoft CDN connections fail with ENETUNREACH/ETIMEDOUT. Prefer
// IPv4 so downloads use a working route.
dns.setDefaultResultOrder('ipv4first');

let mainWindow = null;
let accountManager = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#1b1b1f',
    frame: false,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.loadFile(path.join(__dirname, '..', 'index.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// Forward launcher progress to the renderer's onMinecraftStatus listener.
// Unwraps nested/aggregate errors into a single readable message so the
// renderer shows the real cause instead of a bare "AggregateError".
function formatError(error) {
  if (!error) return 'Unknown error';
  const parts = [];
  const seen = new Set();

  const walk = (err, depth = 0) => {
    if (!err || depth > 4 || seen.has(err)) return;
    seen.add(err);
    const msg = err.message || err.code || String(err);
    if (msg && msg !== 'AggregateError' && !parts.includes(msg)) parts.push(msg);
    if (Array.isArray(err.errors)) err.errors.forEach((e) => walk(e, depth + 1));
    if (err.cause) walk(err.cause, depth + 1);
  };

  walk(error);
  return parts.length ? parts.slice(0, 4).join(' | ') : 'Unknown error';
}

function sendStatus(payload) {
  log.info('[status]', payload);
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('minecraft:status', payload);
  }
}

function sanitizeClientOptions(options) {
  if (!options || typeof options !== 'object' || Array.isArray(options)) return null;

  const clean = {};
  Object.entries(options).forEach(([key, value]) => {
    if (!/^[A-Za-z0-9_-]{1,48}$/.test(key)) return;
    if (typeof value === 'boolean') {
      clean[key] = value;
    } else if (typeof value === 'number' && Number.isFinite(value)) {
      clean[key] = value;
    } else if (typeof value === 'string' && value.length <= 64) {
      clean[key] = value;
    }
  });

  return clean;
}

function registerIpc() {
  ipcMain.handle('app:getMeta', () => ({
    version: app.getVersion(),
    platform: process.platform,
    hasMicrosoftClientId: Boolean(getMicrosoftClientId()),
    configPath: LOCAL_CONFIG_PATH
  }));

  ipcMain.handle('account:get', () => accountManager.snapshot());
  ipcMain.handle('account:login', () => accountManager.login(mainWindow));
  ipcMain.handle('account:logout', () => accountManager.logout());

  ipcMain.handle('settings:save', async (_event, patch = {}) => {
    const next = {};
    if (typeof patch.ramGb === 'number') {
      next.maxMemoryMb = Math.round(patch.ramGb * 1024);
    }
    if (typeof patch.maxMemoryMb === 'number') {
      next.maxMemoryMb = patch.maxMemoryMb;
    }
    if (typeof patch.versionId === 'string') {
      next.version = patch.versionId;
    }
    if (typeof patch.minecraftDirectory === 'string') {
      next.minecraftDirectory = patch.minecraftDirectory;
    }
    const clientOptions = sanitizeClientOptions(patch.clientOptions);
    if (clientOptions) {
      next.clientOptions = clientOptions;
    }
    return accountManager.settingsStore.write(next);
  });

  ipcMain.handle('settings:selectDirectory', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Select Minecraft Directory',
      properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || !result.filePaths.length) {
      return '';
    }
    const directory = result.filePaths[0];
    await accountManager.settingsStore.write({ minecraftDirectory: directory });
    return directory;
  });

  ipcMain.handle('minecraft:versions', () => getMinecraftVersions());

  ipcMain.handle('minecraft:launch', async (_event, options = {}) => {
    log.info('IPC minecraft:launch', options);
    try {
      const credentials = await accountManager.getLaunchCredentials();
      const settings = await accountManager.settingsStore.read();

      const versionId = options.versionId || settings.version || '1.20.6';
      const maxMemoryMb =
        typeof options.ramGb === 'number'
          ? Math.round(options.ramGb * 1024)
          : settings.maxMemoryMb;

      return await launchMinecraft(
        app,
        {
          accessToken: credentials.accessToken,
          profile: credentials.profile,
          gameDir: settings.minecraftDirectory,
          versionId,
          maxMemoryMb,
          minMemoryMb: settings.minMemoryMb,
          clientOptions: settings.clientOptions || {}
        },
        sendStatus
      );
    } catch (error) {
      log.error('launch failed:', error);
      const message = formatError(error);
      sendStatus({ stage: 'error', message });
      throw new Error(message);
    }
  });

  ipcMain.handle('minecraft:launchOffline', async (_event, options = {}) => {
    log.info('IPC minecraft:launchOffline', options);
    try {
      const credentials = accountManager.getOfflineCredentials(options.username);
      const settings = await accountManager.settingsStore.read();

      const versionId = options.versionId || settings.version || '1.20.6';
      const maxMemoryMb =
        typeof options.ramGb === 'number'
          ? Math.round(options.ramGb * 1024)
          : settings.maxMemoryMb;

      return await launchMinecraft(
        app,
        {
          offline: true,
          profile: credentials.profile,
          gameDir: settings.minecraftDirectory,
          versionId,
          maxMemoryMb,
          minMemoryMb: settings.minMemoryMb,
          clientOptions: settings.clientOptions || {}
        },
        sendStatus
      );
    } catch (error) {
      log.error('offline launch failed:', error);
      const message = formatError(error);
      sendStatus({ stage: 'error', message });
      throw new Error(message);
    }
  });

  ipcMain.on('window:minimize', () => mainWindow?.minimize());
  ipcMain.on('window:maximize', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.on('window:close', () => mainWindow?.close());
}

app.whenReady().then(() => {
  const logPath = log.init();
  log.info('App ready. Platform:', process.platform, '| Electron', process.versions.electron);
  log.info('Mirror:', getMirror(), '| Has MS client id:', Boolean(getMicrosoftClientId()));
  log.info('DNS result order: ipv4first');
  accountManager = new AccountManager(app);
  registerIpc();
  createWindow();
  log.info('Window created. Log file at:', logPath);

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

process.on('uncaughtException', (error) => {
  log.error('uncaughtException:', error);
});
process.on('unhandledRejection', (reason) => {
  log.error('unhandledRejection:', reason);
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
