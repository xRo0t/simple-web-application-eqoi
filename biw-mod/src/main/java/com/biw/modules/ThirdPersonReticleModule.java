package com.biw.modules;

import com.biw.BIWMod;
import com.biw.core.RenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;

/** Draws the server-valid aim point for the shoulder camera. */
public final class ThirdPersonReticleModule extends RenderModule {

    private static final int COL_OUTLINE = 0xCC000000;
    private static final int COL_RETICLE = 0xFF3DD6C4;
    private static final int COL_HIT = 0xFFFF4F5E;

    public ThirdPersonReticleModule() {
        super("thirdperson_reticle", true);
    }

    @Override
    public void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        ThirdPersonModule tp = BIWMod.thirdPerson();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (tp == null || mc == null || mc.options == null || !tp.cameraActive() || !tp.trueAimReticle()) return;

        float fov = mc.options.fov * tp.fovMultiplier();
        int x = tp.trueAimReticleX(scaledWidth, scaledHeight, fov);
        int y = tp.trueAimReticleY(scaledHeight, fov);
        boolean onEntity = mc.result != null && mc.result.type == net.minecraft.util.hit.BlockHitResult.Type.ENTITY;
        drawReticle(x, y, onEntity ? COL_HIT : COL_RETICLE);
    }

    private void drawReticle(int x, int y, int color) {
        // Small crisp cross with a dark outline. The vanilla crosshair is hidden
        // while this reticle is active, so this is the only aim point.
        DrawableHelper.fill(x - 1, y - 5, x + 2, y - 1, COL_OUTLINE);
        DrawableHelper.fill(x - 1, y + 2, x + 2, y + 6, COL_OUTLINE);
        DrawableHelper.fill(x - 5, y - 1, x - 1, y + 2, COL_OUTLINE);
        DrawableHelper.fill(x + 2, y - 1, x + 6, y + 2, COL_OUTLINE);
        DrawableHelper.fill(x, y - 4, x + 1, y - 1, color);
        DrawableHelper.fill(x, y + 2, x + 1, y + 5, color);
        DrawableHelper.fill(x - 4, y, x - 1, y + 1, color);
        DrawableHelper.fill(x + 2, y, x + 5, y + 1, color);
    }
}
