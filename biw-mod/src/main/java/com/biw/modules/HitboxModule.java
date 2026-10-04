package com.biw.modules;

import com.biw.core.WorldRenderModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;

/**
 * Shows entity hitbox outlines by toggling the game's own
 * {@link EntityRenderDispatcher#renderHitboxes} flag. This reuses vanilla's
 * outline rendering, so it adds no per-entity iteration or allocation of our
 * own — the lightest possible implementation.
 */
public final class HitboxModule extends WorldRenderModule {

    public HitboxModule() {
        super("hitbox", true);
    }

    @Override
    public void onEnable() {
        apply(true);
    }

    @Override
    public void onDisable() {
        apply(false);
    }

    @Override
    public void onRenderWorld(float partialTicks) {
        // Keep vanilla's flag in sync with our enabled state. Cheap boolean
        // assignment; no allocation.
        apply(isEnabled());
    }

    private void apply(boolean value) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderManager();
        if (dispatcher != null) {
            ((com.biw.mixin.AccessorEntityRenderDispatcher) dispatcher).biw$setRenderHitboxes(value);
        }
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        return s;
    }
}
