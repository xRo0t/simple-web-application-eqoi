package com.biw.core;

/** A module that draws in the 3D world. */
public abstract class WorldRenderModule extends Module {

    protected WorldRenderModule(String id, boolean enabledByDefault) {
        super(id, enabledByDefault);
    }

    /**
     * Called once per world render pass. Keep this allocation-light.
     *
     * @param partialTicks frame interpolation factor
     */
    public abstract void onRenderWorld(float partialTicks);
}
