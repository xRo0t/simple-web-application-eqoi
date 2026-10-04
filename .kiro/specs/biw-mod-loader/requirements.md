# Requirements — BIW Mod (Minecraft 1.8.9)

## Introduction

BIW Mod is a client-side Minecraft 1.8.9 modification that adds a hitbox
outline, a lightweight info HUD, a zoom key, and a SOP (use-and-drop) helper,
loaded through LegacyFabric (Fabric Loader + SpongePowered Mixin for 1.8.9). The
existing BIW Electron launcher is extended to install LegacyFabric, place the
BIW mod jar in `mods/`, and launch the modded version. These requirements are
derived from `design.md`.

Scope for this milestone: working loader + Hitbox first, then HUD and Zoom.
Performance must not measurably regress versus vanilla.

## Requirements

### Requirement 1 — Mod loader bootstrap

**User Story:** As a player, I want the BIW mod to load into Minecraft 1.8.9 so
that its features run alongside the vanilla game.

#### Acceptance Criteria
1. WHEN the game launches with the LegacyFabric version (main class
   `net.fabricmc.loader.impl.launch.knot.KnotClient`) THEN the system SHALL
   initialize Fabric Loader and SpongePowered Mixin and apply `biw.mixins.json`.
2. WHEN Fabric Loader starts THEN it SHALL discover the BIW mod from `mods/` via
   its `fabric.mod.json` and invoke the `com.biw.BIWMod` client entrypoint.
3. IF a mixin fails to apply THEN the system SHALL log the failure, disable the
   affected feature, and allow the game to continue running.
4. WHEN the game reaches the main menu THEN BIW SHALL have registered its
   modules without throwing.

### Requirement 2 — Module framework

**User Story:** As a developer, I want a small module framework so that each
feature is independent, toggleable, and configurable.

#### Acceptance Criteria
1. WHEN BIW initializes THEN the system SHALL register each module with a unique
   id and an enabled/disabled state.
2. WHEN a module callback (render/observe) throws THEN the system SHALL catch
   the error so other modules and the render loop are unaffected.
3. WHEN a module is disabled THEN the system SHALL stop invoking its callbacks.
4. WHERE a module exposes settings THEN the system SHALL load and persist them
   via the JSON config.

### Requirement 3 — Hitbox outline

**User Story:** As a player, I want to see an outline around entities so that
their hitboxes are easier to read.

#### Acceptance Criteria
1. WHEN the world render pass completes AND the hitbox module is enabled THEN
   the system SHALL draw an outline around each loaded living entity using its
   existing bounding box.
2. WHEN `theWorld` is null OR no entities are loaded THEN the system SHALL draw
   nothing and SHALL NOT throw.
3. WHERE the user configures color and line width THEN the system SHALL render
   using those values.
4. WHEN the hitbox module is disabled THEN no outline SHALL be drawn.

### Requirement 4 — Info HUD

**User Story:** As a player, I want a small on-screen HUD so that I can see FPS
and optional info while playing.

#### Acceptance Criteria
1. WHEN the HUD overlay renders AND the HUD module is enabled THEN the system
   SHALL display the current FPS.
2. WHERE coordinates are enabled AND a player exists THEN the system SHALL
   display the player's XYZ position.
3. WHERE ping display is enabled THEN the system SHALL display the player's
   network latency.
4. WHERE the user configures the HUD position THEN the system SHALL render at
   that position.
5. WHEN the HUD module is disabled THEN no HUD SHALL be drawn.

### Requirement 5 — Zoom

**User Story:** As a player, I want a zoom key so that I can see distant things
more clearly, like OptiFine's zoom.

#### Acceptance Criteria
1. WHILE the configured zoom key is held AND the zoom module is enabled THEN the
   system SHALL reduce the field of view by the configured zoom level.
2. WHEN the zoom key is released THEN the system SHALL restore the normal field
   of view.
3. THE zoom field-of-view multiplier SHALL always be in the range `(0, 1]`, and
   SHALL equal `1` when zoom is inactive.
4. WHEN the zoom module is disabled THEN holding the key SHALL have no effect.

### Requirement 6 — Configuration persistence

**User Story:** As a player, I want my settings saved so that they persist
between sessions.

#### Acceptance Criteria
1. WHEN a module's settings change THEN the system SHALL persist them to
   `config/biw.json`.
2. WHEN the game starts THEN the system SHALL load settings from `config/biw.json`.
3. IF the config file is missing or invalid THEN the system SHALL fall back to
   defaults and rewrite a clean config file.
4. WHEN settings are serialized and parsed back THEN the result SHALL equal the
   original configuration (round-trip).

### Requirement 7 — Launcher integration

**User Story:** As a player, I want to launch the modded version from the BIW
launcher so that I don't manage files manually.

#### Acceptance Criteria
1. WHEN the launcher prepares a modded launch THEN the system SHALL fetch the
   LegacyFabric profile JSON for `1.8.9` from the LegacyFabric meta API and
   write it to `versions/fabric-loader-<loader>-1.8.9/` so it inherits from the
   installed vanilla `1.8.9`.
2. WHEN the launcher prepares a modded launch THEN the system SHALL install the
   Fabric Loader, intermediary, and LWJGL libraries (resolving LegacyFabric and
   Fabric mavens) and copy the BIW mod jar into `mods/`.
3. WHEN launching 1.8.9 THEN the launcher SHALL launch the LegacyFabric version;
   `clientOptions.modded === false` SHALL fall back to vanilla.
4. WHEN launching the modded version THEN the launcher SHALL use Java 8
   (`jre-legacy`).
5. WHEN the generated LegacyFabric version JSON is parsed by `@xmcl/core`
   `Version.parse` THEN it SHALL resolve successfully with the Knot main class.
6. IF the LegacyFabric version JSON or libraries are missing at launch THEN the
   launcher SHALL reinstall them, OR surface a clear error if the mod jar is
   absent (and fall back to vanilla).

### Requirement 8 — Performance

**User Story:** As a player, I want the mod to stay light so that my frame rate
is not hurt.

#### Acceptance Criteria
1. THE mod SHALL NOT create background threads, timers, or network I/O for the
   Hitbox, HUD, or Zoom modules.
2. WHEN rendering the hitbox THEN the system SHALL iterate loaded entities once
   per render pass and SHALL avoid per-entity heap allocation in the hot path.
3. WHEN rendering the HUD THEN the system SHALL avoid allocation-heavy
   formatting in the per-frame path.
4. WHERE a module is disabled THEN it SHALL add no per-frame work beyond an
   enabled-state check.

## Out of scope (non-goals)

- Any feature that performs gameplay actions automatically or combines multiple
  game actions into one input.
- Server-side components, online cosmetics visible to other players.
- Versions other than 1.8.9 for this milestone.
