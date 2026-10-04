package com.biw.modules;

import com.biw.BIWMod;
import com.biw.core.RenderModule;
import com.biw.mixin.InvokerMinecraftClient;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Mouse;

import java.util.Random;

/**
 * CPS-limited left-click assistant. It uses Minecraft's own attack action and
 * is hard-capped at 14 CPS for servers that allow that limit.
 */
public final class AutoClickerModule extends RenderModule {

    private static final int HARD_MAX_CPS = 14;
    private static final int HARD_MIN_CPS = 1;
    private static final long STALE_CADENCE_MS = 250L;
    private static final long HARD_MIN_INTERVAL_MS = ceilDiv(1000L, HARD_MAX_CPS);

    private int minCps = 12;
    private int maxCps = 13;
    private boolean holdLeftToActivate = true;
    private boolean targetOnly = false;
    private boolean pauseOnBlocks = true;
    private boolean smartPvp = true;
    private boolean lockedCadence = false;

    private final Random random = new Random();
    private long nextClickAt = 0L;
    private long lastQueuedAt = 0L;
    private boolean leftWasDown = false;
    private boolean wasTargetingEntity = false;

    public AutoClickerModule() {
        super("autoclicker", false);
    }

    @Override
    public void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null || mc.options.attackKey == null) return;

        if (mc.currentScreen != null) {
            resetCadence();
            return;
        }

        boolean leftDown = safeButton(0);
        boolean active = !holdLeftToActivate || leftDown;
        if (!active) {
            resetCadence();
            return;
        }

        long now = System.currentTimeMillis();

        // The physical press already creates the first click; start cleanly
        // after that so enabling/holding never creates a burst.
        if (holdLeftToActivate && leftDown && !leftWasDown) {
            leftWasDown = true;
            nextClickAt = now + intervalMs();
            return;
        }
        leftWasDown = leftDown;

        if (!shouldClick(mc, now)) {
            nextClickAt = Math.max(nextClickAt, now + HARD_MIN_INTERVAL_MS);
            return;
        }

        if (now < nextClickAt) return;
        if (now - lastQueuedAt < HARD_MIN_INTERVAL_MS) {
            nextClickAt = lastQueuedAt + HARD_MIN_INTERVAL_MS;
            return;
        }

        clickAttack(mc);
        CpsModule cps = BIWMod.MODULES.get(CpsModule.class);
        if (cps != null) cps.recordLeftClick(now);
        lastQueuedAt = now;

        long interval = intervalMs();
        if (now - nextClickAt > STALE_CADENCE_MS) {
            nextClickAt = now + interval;
        } else {
            nextClickAt += interval;
            if (nextClickAt <= now) nextClickAt = now + interval;
        }
        if (nextClickAt - lastQueuedAt < HARD_MIN_INTERVAL_MS) {
            nextClickAt = lastQueuedAt + HARD_MIN_INTERVAL_MS;
        }
    }

    private long intervalMs() {
        int lo = minCps();
        int hi = clampCps(maxCps);
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }

        long minInterval = ceilDiv(1000L, hi);
        if (lockedCadence) return minInterval;

        long maxInterval = 1000L / lo;
        if (maxInterval <= minInterval) return minInterval;

        double span = maxInterval - minInterval;
        double mean = minInterval + span * 0.25D;
        double stdDev = Math.max(1.0D, span * 0.18D);
        double sample = mean + random.nextGaussian() * stdDev;

        if (sample < minInterval) sample = minInterval;
        if (sample > maxInterval) sample = maxInterval;
        return (long) sample;
    }

    private boolean shouldClick(MinecraftClient mc, long now) {
        BlockHitResult.Type type = mc.result == null ? null : mc.result.type;
        if (type == BlockHitResult.Type.ENTITY) {
            if (smartPvp && !wasTargetingEntity && now - lastQueuedAt >= HARD_MIN_INTERVAL_MS) {
                nextClickAt = Math.min(nextClickAt == 0L ? now : nextClickAt, now);
            }
            wasTargetingEntity = true;
            return true;
        }

        wasTargetingEntity = false;
        if (smartPvp) return false;
        if (targetOnly) return false;
        return !(pauseOnBlocks && type == BlockHitResult.Type.BLOCK);
    }

    private void clickAttack(MinecraftClient mc) {
        try {
            ((InvokerMinecraftClient) mc).biw$doAttack();
        } catch (Throwable ignored) {
            KeyBinding.onKeyPressed(mc.options.attackKey.getCode());
        }
    }

    private int clampCps(int value) {
        if (value < HARD_MIN_CPS) return HARD_MIN_CPS;
        if (value > HARD_MAX_CPS) return HARD_MAX_CPS;
        return value;
    }

    private boolean safeButton(int button) {
        try {
            return Mouse.isButtonDown(button);
        } catch (Throwable t) {
            return false;
        }
    }

    private void resetCadence() {
        leftWasDown = false;
        wasTargetingEntity = false;
        nextClickAt = 0L;
        lastQueuedAt = 0L;
    }

    private static long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1L) / denominator;
    }

    public int minCps() {
        return clampCps(minCps);
    }

    public int maxCps() {
        return clampCps(maxCps);
    }

    public void cycleMaxCps() {
        maxCps = maxCps >= HARD_MAX_CPS ? 10 : maxCps + 1;
        if (minCps > maxCps) minCps = maxCps;
    }

    public void cycleMinCps() {
        minCps = minCps >= maxCps ? 10 : minCps + 1;
    }

    public boolean holdLeftToActivate() {
        return holdLeftToActivate;
    }

    public void toggleHoldLeft() {
        holdLeftToActivate = !holdLeftToActivate;
    }

    public boolean smartPvp() {
        return smartPvp;
    }

    public void toggleSmartPvp() {
        smartPvp = !smartPvp;
        resetCadence();
    }

    public boolean lockedCadence() {
        return lockedCadence;
    }

    public void toggleLockedCadence() {
        lockedCadence = !lockedCadence;
        resetCadence();
    }

    public boolean targetOnly() {
        return targetOnly;
    }

    public void toggleTargetOnly() {
        targetOnly = !targetOnly;
    }

    public boolean pauseOnBlocks() {
        return pauseOnBlocks;
    }

    public void togglePauseOnBlocks() {
        pauseOnBlocks = !pauseOnBlocks;
    }

    @Override
    protected void onDisable() {
        resetCadence();
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("minCps")) minCps = clampCps(s.get("minCps").getAsInt());
        if (s.has("maxCps")) maxCps = clampCps(s.get("maxCps").getAsInt());
        if (s.has("holdLeftToActivate")) holdLeftToActivate = s.get("holdLeftToActivate").getAsBoolean();
        if (s.has("smartPvp")) smartPvp = s.get("smartPvp").getAsBoolean();
        if (s.has("lockedCadence")) lockedCadence = s.get("lockedCadence").getAsBoolean();
        if (s.has("targetOnly")) targetOnly = s.get("targetOnly").getAsBoolean();
        if (s.has("pauseOnBlocks")) pauseOnBlocks = s.get("pauseOnBlocks").getAsBoolean();
        if (minCps > maxCps) minCps = maxCps;
        if (lockedCadence && maxCps >= HARD_MAX_CPS) lockedCadence = false;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("minCps", minCps());
        s.addProperty("maxCps", maxCps());
        s.addProperty("holdLeftToActivate", holdLeftToActivate);
        s.addProperty("smartPvp", smartPvp);
        s.addProperty("lockedCadence", lockedCadence);
        s.addProperty("targetOnly", targetOnly);
        s.addProperty("pauseOnBlocks", pauseOnBlocks);
        return s;
    }
}
