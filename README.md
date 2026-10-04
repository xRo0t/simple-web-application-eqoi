# BIW Client

A clean Minecraft client launcher (Lunar-style) with profiles, modules,
performance controls, servers, and cosmetics. The UI lives in `frontend/`
and the launching logic runs in an Electron main process under
`frontend/main/`.

## Architecture

```text
frontend/
  index.html            UI markup
  app.js                renderer logic (calls window.biwClient.*)
  styles.css            styles
  main/
    main.js             Electron main process + IPC handlers
    preload.js          secure bridge -> window.biwClient
    accountManager.js   ties auth + token store + settings together
    config.js           client id / paths / config.local.json
    settingsStore.js    persisted launch settings
    tokenStore.js       encrypted token storage (safeStorage)
    auth/
      microsoftAuth.js  Microsoft OAuth (PKCE)
      minecraftAuth.js  Xbox Live + Minecraft Services auth
    launcher/
      minecraftLauncher.js  version + Java install and launch (@xmcl)
```

The renderer talks to the main process only through `window.biwClient`,
which is exposed by `preload.js` over IPC. Tokens never reach the renderer.

## Setup

1. Install dependencies:

   ```powershell
   npm install
   ```

2. Register an Azure app (Microsoft Entra) as a public client
   ("Mobile and desktop applications") and add this redirect URI:

   ```text
   http://127.0.0.1:42829/auth/callback
   ```

3. Copy the example config and add your client id:

   ```powershell
   Copy-Item config.local.example.json config.local.json
   ```

   Then set `microsoftClientId` in `config.local.json`. You can also use the
   `BIW_MS_CLIENT_ID` environment variable instead.

## Run

```powershell
npm start
```

The first launch of any Minecraft version downloads that version and, if no
suitable local Java is found, a Mojang Java runtime. Progress is reported
live in the launch panel.

## Notes

- The original Sooryn/Dolet entry (`backend/main.dlt`, `sooryn.app`) is kept
  for reference; the launcher itself runs on Electron because the launch and
  install layer depends on `@xmcl`.
- `config.local.json` is git-ignored. Never commit your client id secrets.
