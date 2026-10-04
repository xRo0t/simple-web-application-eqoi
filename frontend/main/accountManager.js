const { ensureMicrosoftAccessToken, loginWithMicrosoft } = require('./auth/microsoftAuth');
const { authenticateMinecraft } = require('./auth/minecraftAuth');
const { TokenStore } = require('./tokenStore');
const { SettingsStore } = require('./settingsStore');
const crypto = require('node:crypto');

// Builds the same offline UUID Minecraft uses for cracked/offline players:
// a name-based (v3) UUID from the MD5 of "OfflinePlayer:<name>".
function offlineUuid(name) {
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${name}`).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30; // version 3
  hash[8] = (hash[8] & 0x3f) | 0x80; // IETF variant
  return hash.toString('hex');
}

// Ties together Microsoft OAuth, Minecraft (Xbox) auth, the encrypted token
// store, and the settings store into the single shape the renderer expects.
class AccountManager {
  constructor(app) {
    this.app = app;
    this.tokenStore = new TokenStore(app);
    this.settingsStore = new SettingsStore(app);
  }

  // Shape returned to the renderer. Never includes raw tokens.
  async snapshot() {
    const tokens = await this.tokenStore.read().catch(() => ({}));
    const settings = await this.settingsStore.read();
    const profile = tokens.profile || null;

    return {
      signedIn: Boolean(profile && tokens.minecraft?.accessToken),
      profile,
      settings: {
        minecraftDirectory: settings.minecraftDirectory,
        version: settings.version,
        minMemoryMb: settings.minMemoryMb,
        maxMemoryMb: settings.maxMemoryMb,
        clientOptions: settings.clientOptions || {}
      }
    };
  }

  async login(parentWindow) {
    const microsoft = await loginWithMicrosoft(parentWindow);
    const minecraft = await authenticateMinecraft(microsoft.accessToken);

    await this.tokenStore.write({
      microsoft,
      minecraft: {
        accessToken: minecraft.accessToken,
        expiresAt: minecraft.expiresAt
      },
      profile: minecraft.profile
    });

    return this.snapshot();
  }

  async logout() {
    await this.tokenStore.clear();
    return this.snapshot();
  }

  // Returns a fresh, valid Minecraft access token + profile for launching,
  // refreshing the Microsoft token and re-authenticating with Xbox if needed.
  async getLaunchCredentials() {
    const tokens = await this.tokenStore.read();
    if (!tokens.profile) {
      throw new Error('No saved account. Sign in first.');
    }

    const microsoft = await ensureMicrosoftAccessToken(tokens.microsoft);
    let minecraft = tokens.minecraft;

    const needsRefresh =
      microsoft !== tokens.microsoft ||
      !minecraft?.accessToken ||
      !(minecraft.expiresAt > Date.now() + 60_000);

    if (needsRefresh) {
      const result = await authenticateMinecraft(microsoft.accessToken);
      minecraft = {
        accessToken: result.accessToken,
        expiresAt: result.expiresAt
      };
      await this.tokenStore.write({
        microsoft,
        minecraft,
        profile: result.profile
      });
      tokens.profile = result.profile;
    }

    return {
      accessToken: minecraft.accessToken,
      profile: tokens.profile
    };
  }

  // Offline/dev credentials: no auth, just a name-based offline profile.
  // For local testing and single-player; rejected by online-mode servers.
  getOfflineCredentials(username) {
    const name = String(username || '').trim() || 'Player';
    if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) {
      throw new Error('Offline name must be 1-16 letters, numbers, or underscores.');
    }
    return {
      profile: { id: offlineUuid(name), name }
    };
  }
}

module.exports = {
  AccountManager
};
