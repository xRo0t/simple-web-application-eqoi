const fs = require('node:fs/promises');
const path = require('node:path');
const { safeStorage } = require('electron');

class TokenStore {
  constructor(app) {
    this.path = path.join(app.getPath('userData'), 'tokens.bin');
  }

  async read() {
    try {
      const encrypted = await fs.readFile(this.path);
      if (!safeStorage.isEncryptionAvailable()) {
        throw new Error('Electron safeStorage encryption is unavailable on this system.');
      }
      const json = safeStorage.decryptString(encrypted);
      return JSON.parse(json);
    } catch (error) {
      if (error.code === 'ENOENT') {
        return {};
      }
      throw error;
    }
  }

  async write(data) {
    if (!safeStorage.isEncryptionAvailable()) {
      throw new Error('Electron safeStorage encryption is unavailable on this system.');
    }

    await fs.mkdir(path.dirname(this.path), { recursive: true });
    const encrypted = safeStorage.encryptString(JSON.stringify(data, null, 2));
    await fs.writeFile(this.path, encrypted);
  }

  async clear() {
    await fs.rm(this.path, { force: true });
  }
}

module.exports = {
  TokenStore
};
