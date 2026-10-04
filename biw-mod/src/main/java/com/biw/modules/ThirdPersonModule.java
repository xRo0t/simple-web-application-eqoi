package com.biw.modules;

import com.biw.core.InputModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ThirdPersonModule extends InputModule {

    private static final int PRESET_VERSION = 4;
    private static final float COMBAT_SIDE_SCALE = 0.18f;

    private int shoulder = 1; // 1 = right, -1 = left
    private float distance = 3.0f;
    private float sideOffset = 0.62f;
    private float heightOffset = 0.08f;
    private float fovBoost = 1.04f;
    private boolean aimAlign = false;
    private float aimDistance = 3.2f;
    private boolean combatCentering = true;
    private boolean trueAimReticle = true;
    private int swapKey = Keyboard.KEY_V;
    private int presetVersion = PRESET_VERSION;

    private boolean capturedPerspective = false;
    private int previousPerspective = 0;
    private boolean swapWasDown = false;
    private float combatBlend = 0.0f;

    public ThirdPersonModule() {
        super("thirdperson", false);
    }

    @Override
    public void onKey(int keyCode, boolean pressed) {
        if (keyCode != swapKey) return;
        boolean freshPress = pressed && !swapWasDown;
        swapWasDown = pressed;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (freshPress && mc != null && mc.currentScreen == null) {
            toggleShoulder();
        }
    }

    @Override
    public void onClientTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        if (!capturedPerspective) {
            previousPerspective = mc.options.perspective;
            capturedPerspective = true;
        }

        if (mc.options.perspective != 1) {
            mc.options.perspective = 1;
        }

        updateCombatBlend(mc);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (capturedPerspective && mc != null && mc.options != null && mc.options.perspective == 1) {
            mc.options.perspective = previousPerspective;
        }
        capturedPerspective = false;
        swapWasDown = false;
        combatBlend = 0.0f;
    }

    public boolean cameraActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return isEnabled() && mc != null && mc.options != null && mc.options.perspective == 1;
    }

    public float distance() {
        return distance;
    }

    public float translateX() {
        return -effectiveSideOffset() * shoulder;
    }

    public float translateY() {
        return -heightOffset;
    }

    public float fovMultiplier() {
        return cameraActive() ? fovBoost : 1.0f;
    }

    public boolean aimAlign() {
        return aimAlign;
    }

    public float aimDistance() {
        return aimDistance;
    }

    public boolean combatCentering() {
        return combatCentering;
    }

    public boolean trueAimReticle() {
        return trueAimReticle;
    }

    public float effectiveSideOffset() {
        float scale = 1.0f - combatBlend * (1.0f - COMBAT_SIDE_SCALE);
        return sideOffset * scale;
    }

    public int trueAimReticleX(int scaledWidth, int scaledHeight, float verticalFov) {
        double depth = Math.max(1.0D, distance + aimDistance);
        double aspect = scaledHeight <= 0 ? 1.0D : (double) scaledWidth / (double) scaledHeight;
        double tanX = Math.tan(Math.toRadians(verticalFov) * 0.5D) * aspect;
        if (tanX < 1.0E-4D) return scaledWidth / 2;
        double normalized = effectiveSideOffset() / depth / tanX;
        return scaledWidth / 2 - (int) Math.round(shoulder * normalized * scaledWidth * 0.5D);
    }

    public int trueAimReticleY(int scaledHeight, float verticalFov) {
        double depth = Math.max(1.0D, distance + aimDistance);
        double tanY = Math.tan(Math.toRadians(verticalFov) * 0.5D);
        if (tanY < 1.0E-4D) return scaledHeight / 2;
        double normalized = heightOffset / depth / tanY;
        return scaledHeight / 2 - (int) Math.round(normalized * scaledHeight * 0.5D);
    }

    public float convergenceYaw() {
        if (!aimAlign) return 0.0f;
        return (float) Math.toDegrees(Math.atan2(-translateX(), convergenceDepth()));
    }

    public float convergencePitch() {
        if (!aimAlign) return 0.0f;
        return (float) Math.toDegrees(Math.atan2(translateY(), convergenceDepth()));
    }

    public String shoulderName() {
        return shoulder > 0 ? "Right" : "Left";
    }

    public void toggleShoulder() {
        shoulder = -shoulder;
    }

    public void toggleAimAlign() {
        aimAlign = !aimAlign;
    }

    public void toggleCombatCentering() {
        combatCentering = !combatCentering;
    }

    public void toggleTrueAimReticle() {
        trueAimReticle = !trueAimReticle;
    }

    public void cycleDistance() {
        distance += 0.2f;
        if (distance > 4.2f) distance = 2.6f;
    }

    public void cycleSideOffset() {
        sideOffset += 0.1f;
        if (sideOffset > 1.35f) sideOffset = 0.55f;
    }

    public void cycleHeightOffset() {
        heightOffset += 0.05f;
        if (heightOffset > 0.45f) heightOffset = -0.10f;
    }

    public void cycleFovBoost() {
        fovBoost += 0.02f;
        if (fovBoost > 1.12f) fovBoost = 1.00f;
    }

    public void cycleAimDistance() {
        aimDistance += 0.5f;
        if (aimDistance > 6.0f) aimDistance = 3.0f;
    }

    public String distanceLabel() {
        return oneDecimal(distance);
    }

    public String sideLabel() {
        return oneDecimal(sideOffset);
    }

    public String heightLabel() {
        return oneDecimal(heightOffset);
    }

    public String fovLabel() {
        return Math.round(fovBoost * 100.0f) + "%";
    }

    public String aimDistanceLabel() {
        return oneDecimal(aimDistance);
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        int loadedPresetVersion = s.has("presetVersion") ? s.get("presetVersion").getAsInt() : 0;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("shoulder")) shoulder = s.get("shoulder").getAsInt() < 0 ? -1 : 1;
        if (s.has("distance")) distance = clamp(s.get("distance").getAsFloat(), 2.4f, 4.4f);
        if (s.has("sideOffset")) sideOffset = clamp(s.get("sideOffset").getAsFloat(), 0.4f, 1.5f);
        if (s.has("heightOffset")) heightOffset = clamp(s.get("heightOffset").getAsFloat(), -0.2f, 0.6f);
        if (s.has("fovBoost")) fovBoost = clamp(s.get("fovBoost").getAsFloat(), 1.0f, 1.14f);
        if (s.has("aimAlign")) aimAlign = s.get("aimAlign").getAsBoolean();
        if (s.has("aimDistance")) aimDistance = clamp(s.get("aimDistance").getAsFloat(), 2.5f, 7.0f);
        if (s.has("combatCentering")) combatCentering = s.get("combatCentering").getAsBoolean();
        if (s.has("trueAimReticle")) trueAimReticle = s.get("trueAimReticle").getAsBoolean();
        if (s.has("swapKey")) swapKey = s.get("swapKey").getAsInt();
        if (loadedPresetVersion < PRESET_VERSION) {
            applyPvpPreset();
        }
        presetVersion = PRESET_VERSION;
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("presetVersion", presetVersion);
        s.addProperty("enabled", isEnabled());
        s.addProperty("shoulder", shoulder);
        s.addProperty("distance", distance);
        s.addProperty("sideOffset", sideOffset);
        s.addProperty("heightOffset", heightOffset);
        s.addProperty("fovBoost", fovBoost);
        s.addProperty("aimAlign", aimAlign);
        s.addProperty("aimDistance", aimDistance);
        s.addProperty("combatCentering", combatCentering);
        s.addProperty("trueAimReticle", trueAimReticle);
        s.addProperty("swapKey", swapKey);
        return s;
    }

    private void applyPvpPreset() {
        shoulder = 1;
        distance = 3.0f;
        sideOffset = 0.62f;
        heightOffset = 0.08f;
        fovBoost = 1.04f;
        aimAlign = false;
        aimDistance = 3.2f;
        combatCentering = true;
        trueAimReticle = true;
        combatBlend = 0.0f;
    }

    private float convergenceDepth() {
        return distance + aimDistance;
    }

    private void updateCombatBlend(MinecraftClient mc) {
        boolean active = combatCentering && mc.currentScreen == null && (safeButton(0) || safeButton(1));
        float target = active ? 1.0f : 0.0f;
        combatBlend += (target - combatBlend) * 0.45f;
        if (Math.abs(target - combatBlend) < 0.01f) {
            combatBlend = target;
        }
    }

    private boolean safeButton(int button) {
        try {
            return Mouse.isButtonDown(button);
        } catch (Throwable t) {
            return false;
        }
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private String oneDecimal(float value) {
        return String.valueOf(Math.round(value * 10.0f) / 10.0f);
    }
}
