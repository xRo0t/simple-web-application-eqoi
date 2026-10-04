package com.biw.modules;

import com.biw.BIWMod;
import com.biw.core.RenderModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Mouse;

/**
 * Clicks-per-second HUD. Counts left (and optionally right) mouse-button
 * presses over a sliding one-second window and draws "LMB: n  RMB: m".
 *
 * Click edges are sampled in {@link #onClientTick} (20Hz). That's enough
 * resolution for a CPS readout and avoids hooking the OS mouse callback. Each
 * press timestamp is stored in a small ring buffer; the displayed value is the
 * count of timestamps within the last 1000 ms.
 */
public final class CpsModule extends RenderModule {

    private static final int WINDOW_MS = 1000;
    private static final int MAX_CLICKS = 64; // ring capacity per button

    private boolean showRight = true;
    private int x = 4;
    private int y = 60;

    private final long[] leftClicks = new long[MAX_CLICKS];
    private final long[] rightClicks = new long[MAX_CLICKS];
    private int leftHead = 0;
    private int rightHead = 0;

    private boolean leftWasDown = false;
    private boolean rightWasDown = false;

    private final StringBuilder sb = new StringBuilder(40);

    public CpsModule() {
        super("cps", false);
    }

    @Override
    public void onClientTick() {
        long now = System.currentTimeMillis();

        boolean leftDown = safeButton(0);
        if (leftDown && !leftWasDown) {
            leftClicks[leftHead] = now;
            leftHead = (leftHead + 1) % MAX_CLICKS;
        }
        leftWasDown = leftDown;

        boolean rightDown = safeButton(1);
        if (rightDown && !rightWasDown) {
            rightClicks[rightHead] = now;
            rightHead = (rightHead + 1) % MAX_CLICKS;
        }
        rightWasDown = rightDown;
    }

    private boolean safeButton(int button) {
        try {
            return Mouse.isButtonDown(button);
        } catch (Throwable t) {
            return false;
        }
    }

    private int countWithin(long[] times, long now) {
        int count = 0;
        for (int i = 0; i < times.length; i++) {
            long t = times[i];
            if (t != 0 && now - t <= WINDOW_MS) count++;
        }
        return count;
    }

    public void recordLeftClick(long now) {
        leftClicks[leftHead] = now;
        leftHead = (leftHead + 1) % MAX_CLICKS;
    }

    @Override
    public void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        long now = System.currentTimeMillis();
        sb.setLength(0);
        sb.append("LMB ").append(countWithin(leftClicks, now));
        if (showRight) {
            sb.append("  RMB ").append(countWithin(rightClicks, now));
        }
        AutoClickerModule ac = BIWMod.MODULES.get(AutoClickerModule.class);
        if (ac != null && ac.isEnabled()) {
            sb.append("  AC ").append(ac.minCps()).append("-").append(ac.maxCps());
            if (ac.smartPvp() || ac.lockedCadence()) sb.append(" ");
            if (ac.smartPvp()) sb.append("S");
            if (ac.lockedCadence()) sb.append("L");
        }
        mc.textRenderer.drawWithShadow(sb.toString(), x, y, 0xFFFFFF);
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("showRight")) showRight = s.get("showRight").getAsBoolean();
        if (s.has("x")) x = s.get("x").getAsInt();
        if (s.has("y")) y = s.get("y").getAsInt();
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("showRight", showRight);
        s.addProperty("x", x);
        s.addProperty("y", y);
        return s;
    }
}
