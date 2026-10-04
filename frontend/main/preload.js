const { contextBridge, ipcRenderer } = require('electron');

// Secure bridge exposed to the renderer as window.biwClient.
// Only whitelisted, serializable calls cross the boundary.
contextBridge.exposeInMainWorld('biwClient', {
  // App + account
  getMeta: () => ipcRenderer.invoke('app:getMeta'),
  getAccount: () => ipcRenderer.invoke('account:get'),
  loginMicrosoft: () => ipcRenderer.invoke('account:login'),
  logout: () => ipcRenderer.invoke('account:logout'),

  // Settings + filesystem
  saveSettings: (patch) => ipcRenderer.invoke('settings:save', patch),
  selectGameDirectory: () => ipcRenderer.invoke('settings:selectDirectory'),

  // Minecraft
  getMinecraftVersions: () => ipcRenderer.invoke('minecraft:versions'),
  launchMinecraft: (options) => ipcRenderer.invoke('minecraft:launch', options),
  launchOfflineMinecraft: (options) => ipcRenderer.invoke('minecraft:launchOffline', options),
  onMinecraftStatus: (callback) => {
    if (typeof callback !== 'function') return () => {};
    const listener = (_event, payload) => callback(payload);
    ipcRenderer.on('minecraft:status', listener);
    return () => ipcRenderer.removeListener('minecraft:status', listener);
  },

  // Window controls
  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close')
});
