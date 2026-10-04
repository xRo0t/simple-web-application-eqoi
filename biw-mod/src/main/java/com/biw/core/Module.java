package com.biw.core;

import com.google.gson.JsonObject;

/**
 * Base type for all BIW features. A module observes game state and renders;
 * it must not spawn threads or do work outside the client tick/render path.
 */
public abstract class Module {

    private final String id;
    private boolean enabled;

    protected Module(String id, boolean enabledByDefault) {
        this.id = id;
        this.enabled = enabledByDefault;
    }

    public final String id() {
        return id;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable();
        else onDisable();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called once per client tick while this module is enabled. */
    public void onClientTick() {
    }

    /** Load this module's settings from its config section (may be null). */
    public void loadConfig(JsonObject section) {
    }

    /** Write this module's settings into a fresh config section. */
    public JsonObject saveConfig() {
        return new JsonObject();
    }
}
