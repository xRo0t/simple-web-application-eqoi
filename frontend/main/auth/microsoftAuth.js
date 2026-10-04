const crypto = require('node:crypto');
const http = require('node:http');
const { BrowserWindow } = require('electron');
const { getAuthPort, getMicrosoftClientId } = require('../config');

const AUTH_BASE = 'https://login.live.com/oauth20_authorize.srf';
const TOKEN_URL = 'https://login.live.com/oauth20_token.srf';
const SCOPES = 'XboxLive.signin offline_access';

function base64Url(buffer) {
  return buffer
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function createPkcePair() {
  const verifier = base64Url(crypto.randomBytes(64));
  const challenge = base64Url(crypto.createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

async function readResponseJson(response) {
  const text = await response.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }

  if (!response.ok) {
    const message = body.error_description || body.error || response.statusText;
    throw new Error(`Microsoft OAuth failed: ${message}`);
  }

  return body;
}

async function exchangeCodeForTokens({ clientId, code, codeVerifier, redirectUri }) {
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({
      client_id: clientId,
      code,
      code_verifier: codeVerifier,
      grant_type: 'authorization_code',
      redirect_uri: redirectUri
    })
  });

  const body = await readResponseJson(response);
  return normalizeMicrosoftTokens(body);
}

async function refreshMicrosoftTokens(clientId, refreshToken) {
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({
      client_id: clientId,
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      scope: SCOPES
    })
  });

  const body = await readResponseJson(response);
  return normalizeMicrosoftTokens(body);
}

function normalizeMicrosoftTokens(body) {
  const expiresIn = Number(body.expires_in || 3600);
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    scope: body.scope,
    tokenType: body.token_type,
    expiresAt: Date.now() + expiresIn * 1000
  };
}

function createCallbackServer({ port, state }) {
  let settled = false;
  let server;
  let rejectCode;

  const code = new Promise((resolve, reject) => {
    rejectCode = reject;
    server = http.createServer((request, response) => {
      const url = new URL(request.url, `http://127.0.0.1:${port}`);
      if (url.pathname !== '/auth/callback') {
        response.writeHead(404);
        response.end('Not found');
        return;
      }

      const receivedState = url.searchParams.get('state');
      const authCode = url.searchParams.get('code');
      const error = url.searchParams.get('error_description') || url.searchParams.get('error');

      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      response.end(`
        <!doctype html>
        <html>
          <head><title>BIW Client</title></head>
          <body style="background:#252525;color:#f4f4f5;font-family:Segoe UI,sans-serif;padding:32px;text-align:center">
            <h2>BIW Client</h2>
            <p>${error ? 'Microsoft sign-in failed.' : 'Sign-in complete. You can return to BIW Client.'}</p>
          </body>
        </html>
      `);

      settled = true;
      server.close();

      if (error) {
        reject(new Error(`Microsoft sign-in failed: ${error}`));
        return;
      }
      if (!authCode || receivedState !== state) {
        reject(new Error('Microsoft sign-in returned an invalid callback.'));
        return;
      }

      resolve(authCode);
    });
  });

  const ready = new Promise((resolve, reject) => {
    server.once('error', (error) => {
      if (!settled) {
        settled = true;
        rejectCode?.(error);
      }
      reject(error);
    });
    server.listen(port, '127.0.0.1', resolve);
  });

  return {
    ready,
    code,
    close() {
      if (!settled) {
        settled = true;
        server?.close();
      }
    }
  };
}

function createMicrosoftLoginWindow(parentWindow, authUrl) {
  const loginWindow = new BrowserWindow({
    width: 496,
    height: 594,
    minWidth: 496,
    minHeight: 594,
    parent: parentWindow || undefined,
    modal: Boolean(parentWindow),
    title: 'Sign in',
    backgroundColor: '#252525',
    autoHideMenuBar: true,
    resizable: false,
    maximizable: false,
    minimizable: true,
    show: false,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      partition: 'persist:biw-microsoft-login'
    }
  });

  loginWindow.once('ready-to-show', () => loginWindow.show());
  loginWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  loginWindow.loadURL(authUrl);

  return loginWindow;
}

async function loginWithMicrosoft(parentWindow) {
  const clientId = getMicrosoftClientId();
  if (!clientId) {
    throw new Error('Missing Microsoft client id. Set BIW_MS_CLIENT_ID or create config.local.json with microsoftClientId.');
  }

  const port = getAuthPort();
  const redirectUri = `http://127.0.0.1:${port}/auth/callback`;
  const state = base64Url(crypto.randomBytes(24));
  const { verifier, challenge } = createPkcePair();

  const authUrl = new URL(AUTH_BASE);
  authUrl.searchParams.set('client_id', clientId);
  authUrl.searchParams.set('response_type', 'code');
  authUrl.searchParams.set('redirect_uri', redirectUri);
  authUrl.searchParams.set('scope', SCOPES);
  authUrl.searchParams.set('state', state);
  authUrl.searchParams.set('code_challenge', challenge);
  authUrl.searchParams.set('code_challenge_method', 'S256');
  authUrl.searchParams.set('prompt', 'select_account');

  const callbackServer = createCallbackServer({ port, state });
  await callbackServer.ready;

  const loginWindow = createMicrosoftLoginWindow(parentWindow, authUrl.toString());
  const code = await Promise.race([
    callbackServer.code,
    new Promise((_, reject) => {
      loginWindow.once('closed', () => {
        callbackServer.close();
        reject(new Error('Microsoft sign-in was closed before it completed.'));
      });
    })
  ]);

  if (!loginWindow.isDestroyed()) {
    loginWindow.close();
  }

  return exchangeCodeForTokens({
    clientId,
    code,
    codeVerifier: verifier,
    redirectUri
  });
}

async function ensureMicrosoftAccessToken(existingTokens) {
  const clientId = getMicrosoftClientId();
  if (!clientId) {
    throw new Error('Missing Microsoft client id. Set BIW_MS_CLIENT_ID or config.local.json.');
  }

  if (existingTokens?.accessToken && existingTokens.expiresAt > Date.now() + 60_000) {
    return existingTokens;
  }

  if (!existingTokens?.refreshToken) {
    throw new Error('No Microsoft session saved. Sign in first.');
  }

  return refreshMicrosoftTokens(clientId, existingTokens.refreshToken);
}

module.exports = {
  ensureMicrosoftAccessToken,
  loginWithMicrosoft
};
