package com.biw.modules;

import com.biw.core.RenderModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Lightweight info HUD: FPS, optional coordinates and ping. Strings are short
 * and built only for enabled lines to keep per-frame allocation minimal.
 */
public final class InfoHudModule extends RenderModule {

    private boolean showFps = true;
    private boolean showCoords = false;
    private boolean showPing = true;
    private int x = 4;
    private int y = 4;

    private final StringBuilder sb = new StringBuilder(32);

    public InfoHudModule() {
        super("infohud", true);
    }

    @Override
    public void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        int lineY = y;
        final int lineHeight = mc.textRenderer.fontHeight + 2;
        final int color = 0xFFFFFF;

        if (showFps) {
            sb.setLength(0);
            sb.append(com.biw.mixin.AccessorMinecraftClient.biw$getCurrentFps()).append(" FPS");
            mc.textRenderer.drawWithShadow(sb.toString(), x, lineY, color);
            lineY += lineHeight;
        }

        PlayerEntity player = mc.player;
        if (showCoords && player != null) {
            sb.setLength(0);
            sb.append("XYZ ")
              .append((int) Math.floor(player.x)).append(' ')
              .append((int) Math.floor(player.y)).append(' ')
              .append((int) Math.floor(player.z));
            mc.textRenderer.drawWithShadow(sb.toString(), x, lineY, color);
            lineY += lineHeight;
        }

        if (showPing && player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (info != null) {
                sb.setLength(0);
                sb.append(info.getLatency()).append(" ms");
                mc.textRenderer.drawWithShadow(sb.toString(), x, lineY, color);
            }
        }
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("showFps")) showFps = s.get("showFps").getAsBoolean();
        if (s.has("showCoords")) showCoords = s.get("showCoords").getAsBoolean();
        if (s.has("showPing")) showPing = s.get("showPing").getAsBoolean();
        if (s.has("x")) x = s.get("x").getAsInt();
        if (s.has("y")) y = s.get("y").getAsInt();
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("showFps", showFps);
        s.addProperty("showCoords", showCoords);
        s.addProperty("showPing", showPing);
        s.addProperty("x", x);
        s.addProperty("y", y);
        return s;
    }
}
