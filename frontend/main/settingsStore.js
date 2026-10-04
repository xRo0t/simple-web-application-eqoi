const fs = require('node:fs/promises');
const path = require('node:path');
const { getDefaultMinecraftDirectory } = require('./config');

class SettingsStore {
  constructor(app) {
    this.app = app;
    this.path = path.join(app.getPath('userData'), 'settings.json');
  }

  async read() {
    const defaults = {
      minecraftDirectory: getDefaultMinecraftDirectory(this.app),
      version: '1.20.6',
      minMemoryMb: 1024,
      maxMemoryMb: 8192,
      clientOptions: {
        sopMode: false
      }
    };

    try {
      const data = JSON.parse(await fs.readFile(this.path, 'utf8'));
      return {
        ...defaults,
        ...data,
        clientOptions: {
          ...defaults.clientOptions,
          ...(data.clientOptions || {})
        }
      };
    } catch (error) {
      if (error.code !== 'ENOENT') {
        console.warn(`Failed to read settings: ${error.message}`);
      }
      return defaults;
    }
  }

  async write(next) {
    const current = await this.read();
    const data = {
      ...current,
      ...next,
      clientOptions: {
        ...current.clientOptions,
        ...(next.clientOptions || {})
      }
    };
    await fs.mkdir(path.dirname(this.path), { recursive: true });
    await fs.writeFile(this.path, JSON.stringify(data, null, 2));
    return data;
  }
}

module.exports = {
  SettingsStore
};
