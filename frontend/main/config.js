const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const LOCAL_CONFIG_PATH = path.join(ROOT, 'config.local.json');

function readLocalConfig() {
  try {
    return JSON.parse(fs.readFileSync(LOCAL_CONFIG_PATH, 'utf8'));
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.warn(`Failed to read ${LOCAL_CONFIG_PATH}:`, error.message);
    }
    return {};
  }
}

function getMicrosoftClientId() {
  return process.env.BIW_MS_CLIENT_ID || readLocalConfig().microsoftClientId || '';
}

function getAuthPort() {
  const raw = process.env.BIW_AUTH_PORT || readLocalConfig().authPort || 42829;
  return Number(raw) || 42829;
}

// Which download source to use for game/asset/library files:
// "mojang" (official) or "bmclapi" (mirror for restricted regions).
function getMirror() {
  return process.env.BIW_MIRROR || readLocalConfig().mirror || 'mojang';
}

function getDefaultMinecraftDirectory(app) {
  if (process.env.BIW_GAME_DIR) {
    return path.resolve(process.env.BIW_GAME_DIR);
  }

  const configured = readLocalConfig().minecraftDirectory;
  if (configured) {
    return path.resolve(configured.replace(/^~(?=$|[\\/])/, os.homedir()));
  }

  return path.join(app.getPath('userData'), 'minecraft');
}

function getRuntimeDirectory(app) {
  return path.join(app.getPath('userData'), 'runtimes');
}

module.exports = {
  getAuthPort,
  getDefaultMinecraftDirectory,
  getMicrosoftClientId,
  getMirror,
  getRuntimeDirectory,
  LOCAL_CONFIG_PATH
};
