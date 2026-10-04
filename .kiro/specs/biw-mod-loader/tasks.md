# Implementation Plan — BIW Mod (Minecraft 1.8.9)

Build order: loader + Hitbox first (verify in-game), then HUD and Zoom, then
launcher integration. Each task references requirements from `requirements.md`.

- [x] 1. Set up the `biw-mod` Gradle project and 1.8.9 dev environment
  - Create `biw-mod/` with `build.gradle`, `settings.gradle`, `gradle.properties`
  - Pin MC `1.8.9`, MCP stable mappings, Mixin `0.7.x`, LaunchWrapper `1.12`
  - Decide and configure the mappings plugin (legacy loom vs ForgeGradle 2.x)
  - Confirm a `gradle build` produces an empty jar and the dev client runs vanilla
  - _Requirements: 1.1, 1.2_

- [ ] 2. Implement the LaunchWrapper tweaker + Mixin bootstrap
  - [ ] 2.1 Write `BIWTweaker` (ITweaker): pass args through, init Mixin, add config
    - _Requirements: 1.1, 1.2_
  - [ ] 2.2 Create `biw.mixins.json` (client side, refmap) and an empty mixin
    - Verify the game still boots to the main menu with the tweaker attached
    - _Requirements: 1.1, 1.3, 1.4_

- [ ] 3. Build the core module framework
  - [ ] 3.1 Implement `Module`, `RenderModule`, `WorldRenderModule`, `InputModule`
    - _Requirements: 2.1, 2.3_
  - [ ] 3.2 Implement `EventBus` and `ModuleManager` (register/enable/disable)
    - Wrap module callbacks in try/catch so one failure is isolated
    - _Requirements: 2.1, 2.2, 2.3_
  - [ ] 3.3 Implement `Config` (Gson load/save to `config/biw.json`) with defaults
    - _Requirements: 2.4, 6.1, 6.2, 6.3_
  - [ ] 3.4* Write JUnit property test for config round-trip
    - `parse(serialize(cfg)) == cfg` for arbitrary valid configs
    - _Requirements: 6.4_

- [ ] 4. Implement render/world Mixin hooks
  - [ ] 4.1 `MixinGuiIngame.renderGameOverlay@RETURN` -> post `RenderHudEvent`
    - _Requirements: 4.1_
  - [ ] 4.2 `MixinEntityRenderer.renderWorldPass@RETURN` -> post `RenderWorldEvent`
    - _Requirements: 3.1_
  - [ ] 4.3 Add a headless mixin-apply test that targets resolve on 1.8.9 mappings
    - _Requirements: 1.3_

- [ ] 5. Implement the Hitbox module
  - [ ] 5.1 `HitboxModule.onRenderWorld`: outline each living entity's bounding box
    - Interpolate position with partialTicks; reuse buffers, no per-entity alloc
    - _Requirements: 3.1, 3.3, 8.2_
  - [ ] 5.2 Guard against null world / empty entity list (no-op, no throw)
    - _Requirements: 3.2_
  - [ ] 5.3 Honor enabled flag and config (color, line width)
    - _Requirements: 3.3, 3.4, 8.4_
  - [ ] 5.4 Manual smoke test: outlines draw in-game; disabling stops them
    - _Requirements: 3.1, 3.4_

- [ ] 6. Implement the Info HUD module
  - [ ] 6.1 `InfoHudModule.onRenderHud`: FPS line, optional coords, optional ping
    - Avoid allocation-heavy formatting in the per-frame path
    - _Requirements: 4.1, 4.2, 4.3, 8.3_
  - [ ] 6.2 Configurable position; honor enabled flag
    - _Requirements: 4.4, 4.5_

- [ ] 7. Implement the Zoom module
  - [ ] 7.1 `MixinEntityRenderer.getFOVModifier@RETURN` applies zoom multiplier
    - _Requirements: 5.1, 5.2_
  - [ ] 7.2 `ZoomModule`: hold-key state + `fovMultiplier()` in `(0,1]`, `1` when off
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [ ] 7.3* JUnit test for zoom fov math (range + inactive == 1)
    - _Requirements: 5.3_

- [ ] 8. Package the mod for distribution
  - `gradle build` yields `biw-mod-0.1.0.jar` containing classes + mixins json + assets
  - Verify the jar contains `biw.mixins.json` and the tweaker class
  - _Requirements: 1.1, 7.2_

- [x] 9. Launcher integration in `minecraftLauncher.js`
  - [x] 9.1 Fetch LegacyFabric profile JSON (inheritsFrom 1.8.9, Knot main class)
    and write it under `versions/` (see `fabricLoader.js`)
    - _Requirements: 7.1_
  - [x] 9.2 Install Fabric/intermediary/LWJGL libraries (LegacyFabric + Fabric
    mavens) and copy the BIW mod jar into `mods/`
    - _Requirements: 7.2_
  - [x] 9.3 Launch the LegacyFabric version for 1.8.9 with Java 8; opt out via
    `clientOptions.modded === false`
    - _Requirements: 7.3, 7.4_
  - [x] 9.4 Fall back to vanilla on install failure; clear error if mod jar absent
    - _Requirements: 7.6_
  - [x] 9.5 Verify generated JSON resolves with `@xmcl/core` Version.parse
    - _Requirements: 7.5_

- [ ] 10. End-to-end verification
  - Launch `biw-1.8.9` offline from the launcher
  - Confirm: hitbox outlines render, HUD shows FPS, zoom works on key hold
  - Confirm no new threads/network from the mod (performance budget)
  - _Requirements: 1.4, 3.1, 4.1, 5.1, 8.1_

> Tasks marked with `*` are optional property/unit tests. Required tasks have no
> asterisk.
