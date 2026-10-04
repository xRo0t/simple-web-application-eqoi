package com.biw.core;

import com.mojang.blaze3d.platform.GlStateManager;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.Text;

/** Renders one clean frame before F2 screenshots so chat messages stay out. */
public final class ScreenshotCleaner {

    private static boolean hidingMessages = false;

    private ScreenshotCleaner() {
    }

    public static boolean hidingMessages() {
        return hidingMessages;
    }

    public static Text saveWithoutMessages(File runDirectory, int width, int height, Framebuffer framebuffer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.gameRenderer == null || framebuffer == null) {
            return ScreenshotUtils.saveScreenshot(runDirectory, width, height, framebuffer);
        }

        hidingMessages = true;
        try {
            renderCleanFrame(mc, framebuffer);
            return ScreenshotUtils.saveScreenshot(runDirectory, width, height, framebuffer);
        } finally {
            hidingMessages = false;
        }
    }

    private static void renderCleanFrame(MinecraftClient mc, Framebuffer framebuffer) {
        try {
            GlStateManager.clear(16640);
            framebuffer.bind(true);
            mc.gameRenderer.render(1.0F, System.nanoTime());
        } catch (Throwable ignored) {
            // If a one-frame refresh fails, still let the vanilla screenshot path
            // complete instead of breaking F2.
        }
    }
}
