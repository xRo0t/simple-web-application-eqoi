package com.biw.core;

import com.biw.modules.HitboxModule;
import com.biw.modules.InfoHudModule;

/**
 * Minimal, allocation-free dispatcher between mixin hooks and modules.
 *
 * Rather than a generic listener list (which allocates iterators and boxes
 * events), the bus exposes typed dispatch methods that the ModuleManager wires
 * to the relevant modules. This keeps the per-frame render path lean.
 */
public final class EventBus {

    private ModuleManager manager;

    void bind(ModuleManager manager) {
        this.manager = manager;
    }

    /** Posted from MixinGuiIngame at the end of the HUD overlay render. */
    public void postRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        if (manager == null) return;
        manager.dispatchRenderHud(scaledWidth, scaledHeight, partialTicks);
    }

    /** Posted from MixinEntityRenderer at the end of the world render pass. */
    public void postRenderWorld(float partialTicks) {
        if (manager == null) return;
        manager.dispatchRenderWorld(partialTicks);
    }

    /** Posted from the keyboard observe hook. */
    public void postKey(int keyCode, boolean pressed) {
        if (manager == null) return;
        manager.dispatchKey(keyCode, pressed);
    }
}
