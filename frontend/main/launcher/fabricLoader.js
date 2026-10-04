const fs = require('node:fs/promises');
const path = require('node:path');
const { MinecraftFolder, Version } = require('@xmcl/core');
const { installLibraries } = require('@xmcl/installer');
const log = require('../logger');
const { createAgent, ensureGlobal } = require('./netAgent');

// LegacyFabric meta provides a ready-made launcher profile JSON that inherits
// from vanilla 1.8.9 and declares the Fabric loader main class + libraries.
const LEGACY_META = 'https://meta.legacyfabric.net/v2';

// Pinned loader build known to run on Java 8 for 1.8.9. Kept explicit so a
// launch is reproducible rather than tracking "latest".
const DEFAULT_LOADER_VERSION = '0.16.14';

// Where the built mod jar lives in the project tree. ROOT is three levels up
// from this file (launcher -> main -> frontend -> repo root).
const REPO_ROOT = path.resolve(__dirname, '..', '..', '..');
const MOD_JAR_PATH = path.join(REPO_ROOT, 'biw-mod', 'build', 'libs', 'biw-mod-0.1.0.jar');

function gameVersionFromGameDir() {
  return '1.8.9';
}

function fabricVersionId(gameVersion, loaderVersion) {
  return `fabric-loader-${loaderVersion}-${gameVersion}`;
}

async function pathExists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

async function fetchProfileJson(gameVersion, loaderVersion) {
  ensureGlobal();
  const url = `${LEGACY_META}/versions/loader/${gameVersion}/${loaderVersion}/profile/json`;
  log.info(`Fetching LegacyFabric profile JSON: ${url}`);
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`LegacyFabric meta returned ${res.status} for ${url}`);
  }
  return res.json();
}

// Write the Fabric version JSON into versions/<id>/<id>.json so @xmcl/core can
// resolve it (it merges with the inherited vanilla 1.8.9 JSON).
async function writeVersionJson(gameDir, profile) {
  const folder = MinecraftFolder.from(gameDir);
  const id = profile.id;
  const dir = folder.getVersionRoot(id);
  await fs.mkdir(dir, { recursive: true });
  const jsonPath = path.join(dir, `${id}.json`);
  await fs.writeFile(jsonPath, JSON.stringify(profile, null, 2));
  log.info(`Wrote Fabric version JSON: ${jsonPath}`);
  return id;
}

// Copy the BIW mod jar into <gameDir>/mods so Fabric loads it.
async function installModJar(gameDir, send) {
  if (!(await pathExists(MOD_JAR_PATH))) {
    throw new Error(
      `BIW mod jar not found at ${MOD_JAR_PATH}. Build it with: gradlew build (in biw-mod).`
    );
  }
  const modsDir = path.join(gameDir, 'mods');
  await fs.mkdir(modsDir, { recursive: true });

  // Clear previous BIW jars so an old build never shadows the new one.
  const existing = await fs.readdir(modsDir).catch(() => []);
  for (const name of existing) {
    if (/^biw-mod.*\.jar$/i.test(name)) {
      await fs.rm(path.join(modsDir, name)).catch(() => {});
    }
  }

  const dest = path.join(modsDir, path.basename(MOD_JAR_PATH));
  await fs.copyFile(MOD_JAR_PATH, dest);
  log.info(`Installed BIW mod jar -> ${dest}`);
  if (send) {
    send({ stage: 'mod', message: 'BIW mod installed', percent: null });
  }
}

/**
 * Ensure a LegacyFabric modded version is installed and the BIW mod jar is in
 * place. Returns the version id to launch (e.g. fabric-loader-0.16.14-1.8.9).
 *
 * Assumes vanilla 1.8.9 is already installed (the caller installs it first),
 * since the Fabric profile inherits from it.
 */
async function ensureFabric(gameDir, options = {}, send) {
  const gameVersion = gameVersionFromGameDir();
  const loaderVersion = options.loaderVersion || DEFAULT_LOADER_VERSION;
  const id = fabricVersionId(gameVersion, loaderVersion);

  if (send) {
    send({ stage: 'fabric', message: `Installing Fabric loader ${loaderVersion}`, percent: null });
  }

  const profile = await fetchProfileJson(gameVersion, loaderVersion);
  await writeVersionJson(gameDir, profile);

  // Resolve and install the Fabric + intermediary + lwjgl libraries.
  // Some libraries (LegacyFabric's lwjgl natives, intermediary) only live on
  // the LegacyFabric/Fabric mavens, while @xmcl defaults to libraries.minecraft
  // .net + maven central. Add those hosts so every library is tried there too.
  const resolved = await Version.parse(gameDir, id);
  await installLibraries(resolved, {
    dispatcher: createAgent(),
    librariesDownloadConcurrency: 2,
    mavenHost: [
      'https://maven.legacyfabric.net/',
      'https://maven.fabricmc.net/'
    ]
  });
  log.info('Fabric libraries installed');

  await installModJar(gameDir, send);

  if (send) {
    send({ stage: 'fabric', message: 'Fabric loader ready', percent: 100 });
  }
  return id;
}

module.exports = {
  ensureFabric,
  DEFAULT_LOADER_VERSION,
  MOD_JAR_PATH
};
