package com.biw.modules;

import com.biw.core.InputModule;
import com.google.gson.JsonObject;
import org.lwjgl.input.Keyboard;

/**
 * Hold-to-zoom: while the configured key is held, the field of view is reduced
 * by {@code level}. This only changes the local camera FOV; it does not affect
 * gameplay.
 */
public final class ZoomModule extends InputModule {

    private boolean active;
    private float level = 4.0f;
    private int key = Keyboard.KEY_C;

    public ZoomModule() {
        super("zoom", true);
    }

    @Override
    public void onKey(int keyCode, boolean pressed) {
        if (keyCode == key) {
            active = pressed;
        }
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    /**
     * FOV multiplier applied by the EntityRenderer mixin. Always in (0, 1];
     * exactly 1 when zoom is inactive or disabled.
     */
    public float fovMultiplier() {
        if (!isEnabled() || !active) return 1.0f;
        float clampedLevel = level < 1.0f ? 1.0f : level;
        return 1.0f / clampedLevel;
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("level")) level = s.get("level").getAsFloat();
        if (s.has("key")) key = s.get("key").getAsInt();
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("level", level);
        s.addProperty("key", key);
        return s;
    }
}
