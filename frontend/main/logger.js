const fs = require('node:fs');
const path = require('node:path');

// Simple file logger. Writes to biw.log at the project root and truncates it
// on every app start so each run shows a clean trace of what happened.
const LOG_PATH = path.resolve(__dirname, '..', '..', 'biw.log');

let stream = null;

function ts() {
  return new Date().toISOString();
}

function init() {
  try {
    // Truncate (overwrite) the previous run's log.
    stream = fs.createWriteStream(LOG_PATH, { flags: 'w' });
    write('info', `=== BIW Client log started ${ts()} ===`);
    write('info', `Log file: ${LOG_PATH}`);
  } catch (error) {
    console.error('Failed to open log file:', error);
  }
  return LOG_PATH;
}

function serialize(value) {
  if (value instanceof Error) {
    const parts = [`${value.name}: ${value.message}`];
    if (Array.isArray(value.errors)) {
      const uniq = [...new Set(value.errors.map((e) => e?.message || String(e)))];
      uniq.slice(0, 20).forEach((m) => parts.push(`  sub: ${m}`));
    }
    if (value.cause) parts.push(`  cause: ${value.cause.message || value.cause}`);
    if (value.stack) parts.push(value.stack);
    return parts.join('\n');
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

function write(level, ...args) {
  const line = `[${ts()}] [${level.toUpperCase()}] ${args.map(serialize).join(' ')}`;
  // Mirror to the dev console too.
  if (level === 'error') console.error(line);
  else console.log(line);
  try {
    stream?.write(line + '\n');
  } catch {
    // ignore write failures
  }
}

module.exports = {
  init,
  LOG_PATH,
  info: (...a) => write('info', ...a),
  warn: (...a) => write('warn', ...a),
  error: (...a) => write('error', ...a),
  debug: (...a) => write('debug', ...a)
};
