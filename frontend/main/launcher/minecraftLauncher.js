const fs = require('node:fs/promises');
const path = require('node:path');
const { getPlatform } = require('@xmcl/core');
const {
  Version,
  createMinecraftProcessWatcher,
  LaunchPrecheck,
  launch
} = require('@xmcl/core');
const {
  JavaRuntimeTargetType,
  getPotentialJavaLocations,
  getVersionList,
  installJavaRuntimeTask,
  installTask,
  resolveJava
} = require('@xmcl/installer');
const { getRuntimeDirectory, getMirror } = require('../config');
const log = require('../logger');
const { createAgent, createRedirectAgent, ensureGlobal } = require('./netAgent');
const { ensureFabric } = require('./fabricLoader');

const JAVA_RUNTIME_ALL_URL =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

const MOJANG_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const BMCLAPI_BASE = 'https://bmclapi2.bangbang93.com';

// Resolve which download endpoints to use. "bmclapi" routes everything
// through the BMCLAPI mirror, which is reachable from regions where Mojang's
// CDN (resources.download.minecraft.net) times out. "mojang" uses official.
function resolveMirror() {
  const mirror = (getMirror() || 'auto').toLowerCase();
  if (mirror === 'bmclapi') {
    return {
      name: 'bmclapi',
      manifest: `${BMCLAPI_BASE}/mc/game/version_manifest_v2.json`,
      assetsHost: `${BMCLAPI_BASE}/assets`,
      mavenHost: [`${BMCLAPI_BASE}/maven`],
      assetsIndexUrl: (version) =>
        `${BMCLAPI_BASE}/${version.assetIndex?.url?.split('/').slice(3).join('/') || ''}`,
      jarClient: (version) =>
        version.downloads?.client?.url?.replace('https://piston-data.mojang.com', BMCLAPI_BASE),
      javaHost: BMCLAPI_BASE
    };
  }
  return {
    name: 'mojang',
    manifest: MOJANG_MANIFEST,
    assetsHost: undefined,
    mavenHost: undefined,
    assetsIndexUrl: undefined,
    jarClient: undefined,
    javaHost: undefined
  };
}

const VERSION_MANIFEST_V2 = MOJANG_MANIFEST;
const JAVA_BIN = process.platform === 'win32' ? 'java.exe' : 'java';
const VERSION_CACHE_MS = 10 * 60 * 1000;

let versionListCache = null;

function throttle(fn, delay = 250) {
  let last = 0;
  return (...args) => {
    const now = Date.now();
    if (now - last >= delay) {
      last = now;
      fn(...args);
    }
  };
}

async function ensureDir(directory) {
  await fs.mkdir(directory, { recursive: true });
}

async function pathExists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

function sendStatus(send, payload) {
  send({
    time: Date.now(),
    ...payload
  });
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function clampNumber(value, min, max) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  return Math.max(min, Math.min(max, value));
}

function moduleSection(modules, id) {
  const current = modules[id];
  if (isPlainObject(current)) return { ...current };
  return {};
}

async function writeBiwClientConfig(gameDir, clientOptions = {}, send) {
  const configPath = path.join(gameDir, 'config', 'biw.json');
  let root = {};

  try {
    root = JSON.parse(await fs.readFile(configPath, 'utf8'));
    if (!isPlainObject(root)) root = {};
  } catch (error) {
    if (error.code !== 'ENOENT') {
      log.warn(`BIW config read failed, rewriting clean config: ${error.message}`);
    }
  }

  const modules = isPlainObject(root.modules) ? { ...root.modules } : {};

  modules.sop = {
    ...moduleSection(modules, 'sop'),
    enabled: clientOptions.sopMode === true
  };

  const zoomLevel = clampNumber(clientOptions.zoom, 1, 10);
  if (zoomLevel !== null) {
    modules.zoom = {
      ...moduleSection(modules, 'zoom'),
      level: zoomLevel
    };
  }

  const infoHud = moduleSection(modules, 'infohud');
  if (typeof clientOptions.hudFps === 'boolean') infoHud.showFps = clientOptions.hudFps;
  if (typeof clientOptions.hudCoords === 'boolean') infoHud.showCoords = clientOptions.hudCoords;
  if (typeof clientOptions.hudPing === 'boolean') infoHud.showPing = clientOptions.hudPing;
  if (Object.keys(infoHud).length) modules.infohud = infoHud;

  root.modules = modules;
  await ensureDir(path.dirname(configPath));
  await fs.writeFile(configPath, JSON.stringify(root, null, 2));

  sendStatus(send, {
    stage: 'config',
    message: 'Client module settings synced',
    percent: null
  });
}

async function runTask(task, stage, send) {
  const report = throttle((childTask) => {
    const total = task.total || childTask.total || 0;
    const progress = task.progress || childTask.progress || 0;
    const percent = total > 0 ? Math.round((progress / total) * 100) : null;
    sendStatus(send, {
      stage,
      message: childTask.name || stage,
      progress,
      total,
      percent
    });
  });

  return task.startAndWait({
    onStart(childTask) {
      sendStatus(send, {
        stage,
        message: childTask.name || stage,
        progress: task.progress || 0,
        total: task.total || 0,
        percent: null
      });
    },
    onUpdate(childTask) {
      report(childTask);
    },
    onFailed(childTask, error) {
      log.warn(`[task:${stage}] failed: ${childTask.name || stage} ->`, error?.message || String(error));
      sendStatus(send, {
        stage,
        message: `${childTask.name || stage} failed`,
        error: error?.message || String(error)
      });
    },
    onSucceed(childTask) {
      report(childTask);
    }
  });
}

async function resolveVersionMeta(versionId, mirror) {
  const list = await getVersionList({ remote: mirror.manifest, dispatcher: createAgent() });
  const version = list.versions.find((item) => item.id === versionId);
  if (!version) {
    throw new Error(`Minecraft version ${versionId} was not found in the manifest.`);
  }
  return version;
}

async function getMinecraftVersions(limit = 80) {
  const now = Date.now();
  if (versionListCache && now - versionListCache.fetchedAt < VERSION_CACHE_MS) {
    return versionListCache.data;
  }

  const list = await getVersionList({ remote: VERSION_MANIFEST_V2, dispatcher: createAgent() });
  const versions = list.versions
    .filter((item) => item.type === 'release')
    .slice(0, limit)
    .map((item) => ({
      id: item.id,
      type: item.type,
      releaseTime: item.releaseTime,
      url: item.url
    }));

  const data = {
    latest: list.latest,
    versions,
    fetchedAt: now
  };

  versionListCache = { fetchedAt: now, data };
  return data;
}

async function findLocalJava(requiredMajor) {
  // Require an EXACT major match. A newer JDK (e.g. 25) can hang or crash
  // older Minecraft versions, so we only reuse local Java when it matches.
  if (process.env.BIW_JAVA_PATH) {
    const info = await resolveJava(process.env.BIW_JAVA_PATH);
    if (info && info.majorVersion === requiredMajor) {
      return info.path;
    }
  }

  const locations = await getPotentialJavaLocations().catch(() => []);
  for (const location of locations) {
    const info = await resolveJava(location).catch(() => undefined);
    if (info && info.majorVersion === requiredMajor) {
      log.info(`Found matching local Java ${requiredMajor} at ${info.path}`);
      return info.path;
    }
  }

  log.info(`No local Java ${requiredMajor} found; will install Mojang runtime.`);
  return '';
}

// Maps the runtime platform to the keys used in Mojang's java-runtime all.json.
function javaPlatformKey() {
  const p = getPlatform();
  const arch = p.arch;
  if (p.name === 'windows') {
    if (arch === 'x64') return 'windows-x64';
    if (arch === 'x86' || arch === 'ia32') return 'windows-x86';
    if (arch === 'arm64') return 'windows-arm64';
    return 'windows-x64';
  }
  if (p.name === 'osx' || p.name === 'mac') {
    return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  }
  // linux
  return arch === 'x86' || arch === 'ia32' ? 'linux-i386' : 'linux';
}

// Fetch the Java runtime manifest manually. The installer's
// fetchJavaRuntimeManifest throws "invalid throwOnError" with undici 7, so we
// resolve it ourselves via global fetch (which uses our IPv4 dispatcher).
async function fetchJavaManifest(component, mirror) {
  ensureGlobal();
  const allUrl = mirror.javaHost
    ? `${mirror.javaHost}/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json`
    : JAVA_RUNTIME_ALL_URL;

  const all = await (await fetch(allUrl)).json();
  const platformKey = javaPlatformKey();
  const target = all[platformKey]?.[component]?.[0];
  if (!target?.manifest?.url) {
    throw new Error(`No Java runtime "${component}" available for ${platformKey}.`);
  }

  const manifest = await (await fetch(target.manifest.url)).json();
  manifest.target = component;
  manifest.version = target.version;
  return manifest;
}

async function installMojangJavaRuntime(app, resolvedVersion, mirror, send) {
  const component = resolvedVersion.javaVersion?.component || JavaRuntimeTargetType.Gamma;
  const runtimeRoot = getRuntimeDirectory(app);
  const destination = path.join(runtimeRoot, component);
  const javaPath = path.join(destination, 'bin', JAVA_BIN);

  if (await pathExists(javaPath)) {
    return javaPath;
  }

  sendStatus(send, {
    stage: 'java',
    message: `Installing Java runtime: ${component}`,
    percent: null
  });
  log.info(`Fetching Java manifest for ${component} (mirror ${mirror.name})`);

  const manifest = await fetchJavaManifest(component, mirror);
  // Java install breaks with the retry interceptor; use redirect-only.
  const dispatcher = createRedirectAgent();
  await runTask(installJavaRuntimeTask({ destination, manifest, dispatcher }), 'java', send);

  if (!(await pathExists(javaPath))) {
    throw new Error(`Java runtime installed but ${javaPath} was not found.`);
  }
  log.info('Java runtime installed at', javaPath);

  return javaPath;
}

async function ensureJava(app, resolvedVersion, mirror, send) {
  const requiredMajor = Number(resolvedVersion.javaVersion?.majorVersion || 17);
  const localJava = await findLocalJava(requiredMajor);
  if (localJava) {
    sendStatus(send, {
      stage: 'java',
      message: `Using local Java ${requiredMajor}+`,
      percent: 100
    });
    return localJava;
  }

  return installMojangJavaRuntime(app, resolvedVersion, mirror, send);
}

async function installMinecraftVersion(gameDir, versionId, mirror, send) {
  await ensureDir(gameDir);
  log.info(`Installing version ${versionId} via mirror "${mirror.name}" into ${gameDir}`);
  sendStatus(send, {
    stage: 'version',
    message: `Resolving Minecraft ${versionId} (${mirror.name})`,
    percent: null
  });

  const versionMeta = await resolveVersionMeta(versionId, mirror);
  log.info('Resolved version meta:', versionMeta.url);
  const dispatcher = createAgent();
  const options = {
    dispatcher,
    // Low concurrency keeps weak/throttled links stable.
    assetsDownloadConcurrency: 2,
    librariesDownloadConcurrency: 2
  };
  if (mirror.assetsHost) options.assetsHost = mirror.assetsHost;
  if (mirror.mavenHost) options.mavenHost = mirror.mavenHost;
  log.info('Install options:', { assetsHost: options.assetsHost, mavenHost: options.mavenHost, concurrency: 2 });

  const task = installTask(versionMeta, gameDir, options);
  const result = await runTask(task, 'install', send);
  log.info('Version install finished');
  return result;
}

function watchMinecraftProcess(process, send) {
  const watcher = createMinecraftProcessWatcher(process);

  watcher.on('minecraft-window-ready', () => {
    sendStatus(send, {
      stage: 'running',
      message: 'Minecraft window is ready',
      pid: process.pid,
      percent: 100
    });
  });

  watcher.on('minecraft-exit', (event) => {
    sendStatus(send, {
      stage: 'exit',
      message: `Minecraft exited with code ${event.code}`,
      code: event.code,
      signal: event.signal || '',
      crashReportLocation: event.crashReportLocation || ''
    });
  });

  watcher.on('error', (error) => {
    sendStatus(send, {
      stage: 'error',
      message: error?.message || String(error),
      error: error?.message || String(error)
    });
  });
}

async function launchMinecraft(app, options, send) {
  const {
    accessToken,
    gameDir,
    maxMemoryMb = 8192,
    minMemoryMb = 1024,
    offline = false,
    profile,
    versionId = '1.20.6',
    clientOptions = {}
  } = options;

  if (!profile?.id || !profile?.name) {
    throw new Error('Minecraft profile data is missing.');
  }
  if (!offline && !accessToken) {
    throw new Error('Minecraft account data is missing. Sign in first.');
  }

  sendStatus(send, {
    stage: 'start',
    message: `Preparing ${profile.name}${offline ? ' (offline)' : ''}`,
    percent: null
  });

  const mirror = resolveMirror();
  log.info(`launchMinecraft: version=${versionId} offline=${offline} dir=${gameDir} mirror=${mirror.name}`);
  await installMinecraftVersion(gameDir, versionId, mirror, send);
  await writeBiwClientConfig(gameDir, clientOptions, send);

  // The BIW mod targets 1.8.9 via LegacyFabric. When launching 1.8.9, install
  // the Fabric loader + mod jar and launch that version instead of vanilla, so
  // the mod actually loads. Opt out with clientOptions.modded === false.
  let launchVersionId = versionId;
  const wantMod = versionId === '1.8.9' && clientOptions.modded !== false;
  if (wantMod) {
    try {
      launchVersionId = await ensureFabric(gameDir, clientOptions, send);
      log.info(`Modded launch enabled. Version: ${launchVersionId}`);
    } catch (error) {
      log.error('Fabric/mod install failed, falling back to vanilla:', error);
      sendStatus(send, {
        stage: 'mod',
        message: `Mod install failed: ${error.message}. Launching vanilla.`,
        percent: null
      });
      launchVersionId = versionId;
    }
  }

  const resolvedVersion = await Version.parse(gameDir, launchVersionId);
  log.info('Version parsed. Java required:', resolvedVersion.javaVersion?.majorVersion || 'unknown');
  const javaPath = await ensureJava(app, resolvedVersion, mirror, send);
  log.info('Java ready at:', javaPath);

  sendStatus(send, {
    stage: 'launch',
    message: 'Starting Minecraft',
    percent: 100
  });

  log.info('Spawning JVM:', javaPath, '| maxMem', maxMemoryMb, '| user', offline ? 'legacy/offline' : 'msa', '| version', launchVersionId);

  // Use a lean precheck set: ensure natives are extracted, but skip the default
  // linkAssets step. linkAssets copies the entire ~115MB legacy asset tree into
  // assets/virtual/legacy on every launch, which is slow on first run and made
  // the launcher appear to hang at "Spawning JVM". 1.8.9 reads assets fine via
  // --assetsDir + --assetIndex without the virtual copy.
  const prechecks = [LaunchPrecheck.checkNatives];

  let mcProcess;
  try {
    mcProcess = await launch({
      accessToken: offline ? '0' : accessToken,
      gamePath: gameDir,
      gameProfile: {
        id: profile.id,
        name: profile.name
      },
      javaPath,
      launcherBrand: 'BIW Client',
      launcherName: 'BIW Client',
      maxMemory: maxMemoryMb,
      minMemory: minMemoryMb,
      userType: offline ? 'legacy' : 'msa',
      version: resolvedVersion,
      versionName: launchVersionId,
      versionType: 'BIW Client',
      prechecks,
      extraExecOption: {
        cwd: gameDir
      }
    });
  } catch (error) {
    log.error('launch() threw:', error);
    throw error;
  }

  log.info('Minecraft process spawned. pid:', mcProcess.pid);

  // Capture JVM stdout/stderr so crashes are visible in the log.
  mcProcess.stdout?.on('data', (d) => log.info('[mc:out]', d.toString().trim()));
  mcProcess.stderr?.on('data', (d) => log.warn('[mc:err]', d.toString().trim()));
  mcProcess.on('exit', (code, signal) => log.info(`[mc] process exit code=${code} signal=${signal || ''}`));
  mcProcess.on('error', (err) => log.error('[mc] process error:', err));

  watchMinecraftProcess(mcProcess, send);

  return {
    gameDir,
    javaPath,
    offline,
    pid: mcProcess.pid,
    profile,
    versionId
  };
}

module.exports = {
  getMinecraftVersions,
  launchMinecraft
};
