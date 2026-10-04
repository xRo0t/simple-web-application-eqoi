package com.biw.core;

/** A module that draws on the 2D HUD overlay. */
public abstract class RenderModule extends Module {

    protected RenderModule(String id, boolean enabledByDefault) {
        super(id, enabledByDefault);
    }

    /**
     * Called once per HUD render. Keep this allocation-light: it runs every
     * frame.
     *
     * @param scaledWidth  GUI-scaled width
     * @param scaledHeight GUI-scaled height
     * @param partialTicks frame interpolation factor
     */
    public abstract void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks);
}
