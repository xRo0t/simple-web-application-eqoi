// Tests @xmcl launch() directly to capture why Minecraft exits/hangs.
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { Version, launch } = require('@xmcl/core');

const gameDir = path.join(os.homedir(), 'AppData', 'Roaming', 'biw-client', 'minecraft');
const versionId = process.argv[2] || '1.20.6';
const runtimeName = process.argv[3] || 'java-runtime-delta';
const runtime = path.join(os.homedir(), 'AppData', 'Roaming', 'biw-client', 'runtimes', runtimeName, 'bin', 'java.exe');

function offlineUuid(name) {
  const h = crypto.createHash('md5').update(`OfflinePlayer:${name}`).digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  return h.toString('hex');
}

async function main() {
  console.log('Parsing version...');
  const resolved = await Version.parse(gameDir, versionId);
  console.log('Launching with java:', runtime);

  const proc = await launch({
    gamePath: gameDir,
    javaPath: runtime,
    version: resolved,
    accessToken: '0',
    gameProfile: { id: offlineUuid('xRo0t'), name: 'xRo0t' },
    userType: 'legacy',
    maxMemory: 4096,
    minMemory: 1024,
    launcherName: 'BIW',
    launcherBrand: 'BIW',
    extraExecOption: { cwd: gameDir }
  });

  console.log('launch() returned. pid:', proc.pid);
  proc.stdout?.on('data', (d) => process.stdout.write('[out] ' + d));
  proc.stderr?.on('data', (d) => process.stdout.write('[err] ' + d));
  proc.on('exit', (code, sig) => { console.log('EXIT code=', code, 'sig=', sig); process.exit(0); });
  proc.on('error', (e) => { console.log('PROC ERROR', e); process.exit(1); });

  setTimeout(() => { console.log('Still running after 25s (likely OK).'); process.exit(0); }, 25000);
}

main().catch((e) => {
  console.error('LAUNCH FAILED:', e?.name, e?.message);
  if (e?.stack) console.error(e.stack);
  process.exit(1);
});
