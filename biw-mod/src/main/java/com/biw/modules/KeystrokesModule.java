package com.biw.modules;

import com.biw.core.RenderModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.option.GameOptions;
import org.lwjgl.input.Mouse;

/**
 * Keystrokes HUD: draws W/A/S/D, LMB/RMB and a space bar as small boxes that
 * light up when the corresponding input is held. Mirrors the player's actual
 * keybinds (reads movement keys from GameOptions) and the live mouse buttons.
 *
 * Layout (each cell KEY x KEY px, GAP spacing):
 *
 *      [ W ]
 *  [ A ][ S ][ D ]
 *  [ LMB ][ RMB ]
 *  [    SPACE    ]
 */
public final class KeystrokesModule extends RenderModule {

    private static final int KEY = 22;   // cell size
    private static final int GAP = 2;

    // Theme.
    private static final int COL_BG       = 0xB0202028; // idle background
    private static final int COL_BG_ON    = 0xF03DD6C4; // pressed background (teal)
    private static final int COL_BORDER   = 0xFF000000;
    private static final int COL_TXT      = 0xFFE8E8F0;
    private static final int COL_TXT_ON   = 0xFF10202A;

    private int x = 6;
    private int y = 90;

    public KeystrokesModule() {
        super("keystrokes", false);
    }

    @Override
    public void onRenderHud(int scaledWidth, int scaledHeight, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null || mc.options == null) return;

        GameOptions o = mc.options;

        boolean w = isDown(o.forwardKey.getCode());
        boolean a = isDown(o.leftKey.getCode());
        boolean s = isDown(o.backKey.getCode());
        boolean d = isDown(o.rightKey.getCode());
        boolean lmb = safeButton(0);
        boolean rmb = safeButton(1);
        boolean space = isDown(o.jumpKey.getCode());

        int col0 = x;
        int col1 = x + KEY + GAP;
        int col2 = x + 2 * (KEY + GAP);

        int row0 = y;
        int row1 = y + KEY + GAP;
        int row2 = y + 2 * (KEY + GAP);
        int row3 = y + 3 * (KEY + GAP);

        // W (centered over the middle column).
        drawKey(mc, col1, row0, KEY, KEY, "W", w);
        // A S D
        drawKey(mc, col0, row1, KEY, KEY, "A", a);
        drawKey(mc, col1, row1, KEY, KEY, "S", s);
        drawKey(mc, col2, row1, KEY, KEY, "D", d);
        // LMB / RMB (each spans 1.5 cells; keep simple: left half / right half)
        int wideW = (KEY * 3 + GAP * 2 - GAP) / 2;
        drawKey(mc, col0, row2, wideW, KEY, "LMB", lmb);
        drawKey(mc, col0 + wideW + GAP, row2, wideW, KEY, "RMB", rmb);
        // Space (full width, short).
        int fullW = KEY * 3 + GAP * 2;
        drawKey(mc, col0, row3, fullW, 10, "", space);
        // Draw a small "spacebar" tick line label centered.
        String sp = "\u2588\u2588\u2588";
        int spw = mc.textRenderer.getStringWidth(sp);
        mc.textRenderer.draw(sp, col0 + (fullW - spw) / 2, row3 + 1, space ? COL_TXT_ON : COL_TXT);
    }

    private void drawKey(MinecraftClient mc, int x, int y, int w, int h, String label, boolean down) {
        // Border + fill.
        DrawableHelper.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER);
        DrawableHelper.fill(x, y, x + w, y + h, down ? COL_BG_ON : COL_BG);

        if (label != null && !label.isEmpty()) {
            int tw = mc.textRenderer.getStringWidth(label);
            int tx = x + (w - tw) / 2;
            int ty = y + (h - mc.textRenderer.fontHeight) / 2 + 1;
            mc.textRenderer.draw(label, tx, ty, down ? COL_TXT_ON : COL_TXT);
        }
    }

    private boolean isDown(int code) {
        try {
            if (code < 0) {
                // Negative codes are mouse buttons (code = -100 - button).
                return Mouse.isButtonDown(code + 100);
            }
            return org.lwjgl.input.Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean safeButton(int button) {
        try {
            return Mouse.isButtonDown(button);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("x")) x = s.get("x").getAsInt();
        if (s.has("y")) y = s.get("y").getAsInt();
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("x", x);
        s.addProperty("y", y);
        return s;
    }
}
