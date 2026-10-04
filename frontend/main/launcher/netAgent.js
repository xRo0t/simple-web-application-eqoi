const { Agent, interceptors, setGlobalDispatcher } = require('undici');
const log = require('../logger');

// Resilient undici dispatchers tuned for weak/throttled connections in
// restricted regions. Key findings that make downloads work here:
// - Force IPv4 (IPv6 routes are unreachable, causing ENETUNREACH).
// - The `redirect` interceptor is REQUIRED by @xmcl/file-transfer; without it
//   downloads throw "Cannot use 'in' operator to search for 'history'".
// - `retry` makes large asset/library downloads survive transient timeouts.
// - The Java manifest fetch + install break with the retry interceptor
//   ("invalid throwOnError"), so Java uses a redirect-only dispatcher.

const baseConnect = { family: 4, timeout: 30000 };
const baseAgentOpts = {
  connect: baseConnect,
  connectTimeout: 30000,
  headersTimeout: 60000,
  bodyTimeout: 120000,
  keepAliveTimeout: 10000,
  keepAliveMaxTimeout: 30000
};

const RETRY_OPTS = {
  maxRetries: 5,
  minTimeout: 1000,
  maxTimeout: 8000,
  timeoutFactor: 2,
  retryAfter: true,
  errorCodes: [
    'ECONNRESET', 'ECONNREFUSED', 'ETIMEDOUT', 'ENETUNREACH', 'EHOSTUNREACH',
    'EAI_AGAIN', 'UND_ERR_CONNECT_TIMEOUT', 'UND_ERR_HEADERS_TIMEOUT',
    'UND_ERR_BODY_TIMEOUT', 'UND_ERR_SOCKET'
  ],
  statusCodes: [500, 502, 503, 504, 429, 408]
};

let mainAgent = null;
let redirectAgent = null;
let globalSet = false;

// Full dispatcher: IPv4 + redirect + retry. For version/asset/library installs.
function createAgent() {
  if (!mainAgent) {
    mainAgent = new Agent(baseAgentOpts).compose(
      interceptors.redirect({ maxRedirections: 5 }),
      interceptors.retry(RETRY_OPTS)
    );
    log.info('Network agent ready: IPv4 + redirect + retry(5)');
  }
  ensureGlobal();
  return mainAgent;
}

// Redirect-only dispatcher: for Java runtime install (retry breaks it).
function createRedirectAgent() {
  if (!redirectAgent) {
    redirectAgent = new Agent(baseAgentOpts).compose(
      interceptors.redirect({ maxRedirections: 5 })
    );
    log.info('Java network agent ready: IPv4 + redirect');
  }
  ensureGlobal();
  return redirectAgent;
}

// Make global fetch() use IPv4 + redirect too (used for the Java manifest).
function ensureGlobal() {
  if (globalSet) return;
  setGlobalDispatcher(
    new Agent(baseAgentOpts).compose(interceptors.redirect({ maxRedirections: 5 }))
  );
  globalSet = true;
  log.info('Global dispatcher set: IPv4 + redirect');
}

module.exports = { createAgent, createRedirectAgent, ensureGlobal };
