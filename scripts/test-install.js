// Standalone diagnostic: runs version + Java install with the resilient
// IPv4 + retry dispatcher, to confirm downloads survive a throttled link.
const dns = require('node:dns');
dns.setDefaultResultOrder('ipv4first');

const os = require('node:os');
const path = require('node:path');
const { Agent, interceptors, setGlobalDispatcher } = require('undici');
const { Version } = require('@xmcl/core');
const {
  JavaRuntimeTargetType,
  fetchJavaRuntimeManifest,
  getVersionList,
  installJavaRuntimeTask,
  installTask
} = require('@xmcl/installer');

const dispatcher = new Agent({
  connect: { family: 4, timeout: 30000 },
  connectTimeout: 30000,
  headersTimeout: 60000,
  bodyTimeout: 120000
}).compose(
  interceptors.redirect({ maxRedirections: 5 }),
  interceptors.retry({
    maxRetries: 5,
    minTimeout: 1000,
    maxTimeout: 8000,
    errorCodes: ['ECONNRESET', 'ETIMEDOUT', 'ENETUNREACH', 'EHOSTUNREACH', 'EAI_AGAIN',
      'UND_ERR_CONNECT_TIMEOUT', 'UND_ERR_HEADERS_TIMEOUT', 'UND_ERR_BODY_TIMEOUT', 'UND_ERR_SOCKET'],
    statusCodes: [500, 502, 503, 504, 429, 408]
  })
);

// Set as the global default so libraries that don't accept a dispatcher
// (like fetchJavaRuntimeManifest in this version) still use IPv4.
// NOTE: java manifest fetch breaks with the retry interceptor, so the global
// default uses redirect only.
setGlobalDispatcher(
  new Agent({ connect: { family: 4, timeout: 30000 }, connectTimeout: 30000, headersTimeout: 60000, bodyTimeout: 120000 })
    .compose(interceptors.redirect({ maxRedirections: 5 }))
);

const singleRangePolicy = {
  computeRanges(total) {
    return [{ start: 0, end: Math.max(0, total - 1) }];
  }
};

const MIRROR = (process.argv[3] || 'mojang').toLowerCase();
const BASE = 'https://bmclapi2.bangbang93.com';
const versionId = process.argv[2] || '1.20.6';
const gameDir = process.argv[4] === 'real'
  ? path.join(os.homedir(), 'AppData', 'Roaming', 'biw-client', 'minecraft')
  : path.join(os.tmpdir(), 'biw-test-mc3');

const mirror = MIRROR === 'bmclapi'
  ? { manifest: `${BASE}/mc/game/version_manifest_v2.json`, assetsHost: `${BASE}/assets`, mavenHost: [`${BASE}/maven`], javaHost: BASE }
  : { manifest: 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' };

let lastPercent = -1;
function onProgress(task) {
  // noisy; print only when percent changes
}

async function main() {
  console.log(`Mirror: ${MIRROR} | dir: ${gameDir}`);

  console.log('1) version manifest...');
  const list = await getVersionList({ remote: mirror.manifest, dispatcher });
  const meta = list.versions.find((v) => v.id === versionId);
  if (!meta) throw new Error(`Version ${versionId} not found`);
  console.log('   OK');

  console.log('2) installing version (libs + assets, concurrency 2, retries)...');
  const opts = { dispatcher, assetsDownloadConcurrency: 2, librariesDownloadConcurrency: 2 };
  if (mirror.assetsHost) opts.assetsHost = mirror.assetsHost;
  if (mirror.mavenHost) opts.mavenHost = mirror.mavenHost;
  const root = installTask(meta, gameDir, opts);
  await root.startAndWait({
    onUpdate() {
      const p = root.total > 0 ? Math.round((root.progress / root.total) * 100) : 0;
      if (p !== lastPercent) {
        lastPercent = p;
        if (p % 5 === 0) process.stdout.write(`   ${p}%\r`);
      }
    }
  });
  console.log('\n   version install OK');

  console.log('3) Java...');
  const resolved = await Version.parse(gameDir, versionId);
  const component = resolved.javaVersion?.component || JavaRuntimeTargetType.Gamma;
  const javaApi = mirror.javaHost ? { apiHost: mirror.javaHost } : {};

  // Manually fetch the Java runtime manifest (fetchJavaRuntimeManifest is
  // broken with undici 7 -> "invalid throwOnError"). Uses global dispatcher.
  const ALL_URL = mirror.javaHost
    ? `${mirror.javaHost}/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json`
    : 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
  const platform = 'windows-x64';
  const all = await (await fetch(ALL_URL)).json();
  const target = all[platform]?.[component]?.[0];
  if (!target) throw new Error(`No Java target ${component} for ${platform}`);
  const manifest = await (await fetch(target.manifest.url)).json();
  manifest.target = component;
  manifest.version = target.version;

  const javaDispatcher = new Agent({ connect: { family: 4, timeout: 30000 }, connectTimeout: 30000, headersTimeout: 60000, bodyTimeout: 120000 })
    .compose(interceptors.redirect({ maxRedirections: 5 }));
  await installJavaRuntimeTask({ destination: path.join(gameDir, 'runtime', component), manifest, dispatcher: javaDispatcher, ...javaApi }).startAndWait();
  console.log('   Java OK');

  console.log('\nALL STEPS PASSED');
}

main().catch((err) => {
  console.error('\nFAILED:', err?.name, '|', err?.message);
  if (Array.isArray(err?.errors)) {
    const uniq = [...new Set(err.errors.map((e) => e?.message || String(e)))].slice(0, 8);
    uniq.forEach((m, i) => console.error(`  sub[${i}]:`, m));
  }
  process.exit(1);
});
