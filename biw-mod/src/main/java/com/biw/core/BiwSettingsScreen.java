package com.biw.core;

import com.biw.BIWMod;
import com.biw.modules.SopModule;
import com.biw.modules.AutoSoupModule;
import com.biw.modules.AutoClickerModule;
import com.biw.modules.FriendsModule;
import com.biw.modules.GroupModule;
import com.biw.modules.ThirdPersonModule;
import com.biw.core.FriendManager;
import com.biw.core.GroupManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom-drawn BIW settings menu. Instead of vanilla buttons it renders its own
 * panel: a dark rounded-ish card with an accent header, and rows that act as
 * toggles (green ON / red OFF) or value cyclers, with a hover highlight.
 *
 * Two pages: MAIN (module toggles + link to SOP) and SOP (detailed controls).
 * Everything is drawn with {@code fill}/{@code fillGradient} + the text
 * renderer, and clicks are hit-tested manually in {@link #mouseClicked}.
 */
public final class BiwSettingsScreen extends net.minecraft.client.gui.screen.Screen {

    // --- Theme colors (ARGB) --------------------------------------------------
    private static final int COL_DIM      = 0xC0000000; // screen dim
    private static final int COL_PANEL    = 0xF21B1B24; // panel background
    private static final int COL_PANEL_BD = 0xFF3DD6C4; // panel border (teal)
    private static final int COL_HEADER_A = 0xFF1FB6FF; // header gradient top
    private static final int COL_HEADER_B = 0xFF8B5CF6; // header gradient bottom
    private static final int COL_ROW      = 0xFF262633; // row background
    private static final int COL_ROW_HOV  = 0xFF34344A; // row hover
    private static final int COL_ROW_BD   = 0xFF3A3A4D; // row border
    private static final int COL_TEXT     = 0xFFE8E8F0; // primary text
    private static final int COL_SUBTLE   = 0xFF9A9AB0; // subtitle text
    private static final int COL_ON       = 0xFF35D07F; // green
    private static final int COL_OFF      = 0xFFE0556B; // red/pink
    private static final int COL_VALUE    = 0xFFFFC857; // amber value pill

    private static final int PANEL_W = 260;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 5;

    private enum Page { MAIN, SOP, AUTOSOUP, AUTOCLICKER, THIRD_PERSON, FRIENDS, GROUP }

    private enum Kind { TOGGLE, VALUE, LINK, BACK }

    /** A clickable row with bounds resolved at render time. */
    private static final class Row {
        final String label;
        final Kind kind;
        final Runnable action;
        // For toggle: state; for value: text shown in the pill.
        boolean toggled;
        String value;
        int x, y, w, h;

        Row(String label, Kind kind, Runnable action) {
            this.label = label;
            this.kind = kind;
            this.action = action;
        }
    }

    private Page page = Page.MAIN;
    private final List<Row> rows = new ArrayList<>();

    private int panelX, panelY, panelH;

    @Override
    public void init() {
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        if (page == Page.MAIN) {
            buildMain();
        } else if (page == Page.SOP) {
            buildSop();
        } else if (page == Page.AUTOSOUP) {
            buildAutoSoup();
        } else if (page == Page.AUTOCLICKER) {
            buildAutoClicker();
        } else if (page == Page.THIRD_PERSON) {
            buildThirdPerson();
        } else if (page == Page.FRIENDS) {
            buildFriends();
        } else {
            buildGroup();
        }
        layout();
    }

    private void buildMain() {
        for (Module m : BIWMod.MODULES.all()) {
            Row r = new Row(displayName(m.id()), Kind.TOGGLE, () -> m.setEnabled(!m.isEnabled()));
            r.toggled = m.isEnabled();
            rows.add(r);
        }
        rows.add(new Row("SOP settings", Kind.LINK, () -> { page = Page.SOP; rebuild(); }));
        rows.add(new Row("Auto-soup settings", Kind.LINK, () -> { page = Page.AUTOSOUP; rebuild(); }));
        rows.add(new Row("Auto-clicker settings", Kind.LINK, () -> { page = Page.AUTOCLICKER; rebuild(); }));
        rows.add(new Row("Third-person settings", Kind.LINK, () -> { page = Page.THIRD_PERSON; rebuild(); }));
        rows.add(new Row("Friends", Kind.LINK, () -> { page = Page.FRIENDS; rebuild(); }));
        rows.add(new Row("Group", Kind.LINK, () -> { page = Page.GROUP; rebuild(); }));
    }

    private void buildSop() {
        SopModule sop = BIWMod.MODULES.get(SopModule.class);

        Row enable = new Row("Enabled", Kind.TOGGLE, () -> { if (sop != null) sop.setEnabled(!sop.isEnabled()); });
        enable.toggled = sop != null && sop.isEnabled();
        rows.add(enable);

        Row restore = new Row("Switch back to sword", Kind.TOGGLE,
            () -> { if (sop != null) sop.toggleRestorePreviousSlot(); });
        restore.toggled = sop != null && sop.restorePreviousSlot();
        rows.add(restore);

        Row delay = new Row("Drop delay", Kind.VALUE,
            () -> { if (sop != null) sop.cycleDropDelay(); });
        delay.value = sop != null ? sop.dropDelayTicks() + "t" : "-";
        rows.add(delay);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }

    private void buildAutoSoup() {
        AutoSoupModule as = BIWMod.MODULES.get(AutoSoupModule.class);

        Row enable = new Row("Enabled", Kind.TOGGLE,
            () -> { if (as != null) as.setEnabled(!as.isEnabled()); });
        enable.toggled = as != null && as.isEnabled();
        rows.add(enable);

        Row predict = new Row("Predict damage", Kind.TOGGLE,
            () -> { if (as != null) as.togglePredictDamage(); });
        predict.toggled = as != null && as.predictDamage();
        rows.add(predict);

        Row aggro = new Row("Combat aggressiveness", Kind.VALUE,
            () -> { if (as != null) as.cycleAggressiveness(); });
        aggro.value = as != null ? String.valueOf(as.aggressiveness()) : "-";
        rows.add(aggro);

        Row emg = new Row("Emergency at", Kind.VALUE,
            () -> { if (as != null) as.cycleEmergencyHealth(); });
        emg.value = as != null ? hearts(as.emergencyHealth()) : "-";
        rows.add(emg);

        Row waste = new Row("Overheal allowance", Kind.VALUE,
            () -> { if (as != null) as.cycleOverhealAllowance(); });
        waste.value = as != null ? hearts(as.overhealAllowance()) : "-";
        rows.add(waste);

        Row learned = new Row("Learned heal", Kind.VALUE, null);
        learned.value = as != null ? hearts(Math.round(as.learnedHeal())) : "-";
        rows.add(learned);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }

    private void buildAutoClicker() {
        AutoClickerModule ac = BIWMod.MODULES.get(AutoClickerModule.class);

        Row enable = new Row("Enabled", Kind.TOGGLE,
            () -> { if (ac != null) ac.setEnabled(!ac.isEnabled()); });
        enable.toggled = ac != null && ac.isEnabled();
        rows.add(enable);

        Row hold = new Row("Only while holding LMB", Kind.TOGGLE,
            () -> { if (ac != null) ac.toggleHoldLeft(); });
        hold.toggled = ac != null && ac.holdLeftToActivate();
        rows.add(hold);

        Row smart = new Row("Smart PvP", Kind.TOGGLE,
            () -> { if (ac != null) ac.toggleSmartPvp(); });
        smart.toggled = ac != null && ac.smartPvp();
        rows.add(smart);

        Row locked = new Row("Locked max", Kind.TOGGLE,
            () -> { if (ac != null) ac.toggleLockedCadence(); });
        locked.toggled = ac != null && ac.lockedCadence();
        rows.add(locked);

        Row targetOnly = new Row("Target only", Kind.TOGGLE,
            () -> { if (ac != null) ac.toggleTargetOnly(); });
        targetOnly.toggled = ac != null && ac.targetOnly();
        rows.add(targetOnly);

        Row pauseBlocks = new Row("Pause on blocks", Kind.TOGGLE,
            () -> { if (ac != null) ac.togglePauseOnBlocks(); });
        pauseBlocks.toggled = ac != null && ac.pauseOnBlocks();
        rows.add(pauseBlocks);

        Row max = new Row("Max CPS", Kind.VALUE,
            () -> { if (ac != null) ac.cycleMaxCps(); });
        max.value = ac != null ? String.valueOf(ac.maxCps()) : "-";
        rows.add(max);

        Row min = new Row("Min CPS", Kind.VALUE,
            () -> { if (ac != null) ac.cycleMinCps(); });
        min.value = ac != null ? String.valueOf(ac.minCps()) : "-";
        rows.add(min);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }

    private void buildThirdPerson() {
        ThirdPersonModule tp = BIWMod.MODULES.get(ThirdPersonModule.class);

        Row enable = new Row("Enabled", Kind.TOGGLE,
            () -> { if (tp != null) tp.setEnabled(!tp.isEnabled()); });
        enable.toggled = tp != null && tp.isEnabled();
        rows.add(enable);

        Row shoulder = new Row("Shoulder", Kind.VALUE,
            () -> { if (tp != null) tp.toggleShoulder(); });
        shoulder.value = tp != null ? tp.shoulderName() : "-";
        rows.add(shoulder);

        Row distance = new Row("Distance", Kind.VALUE,
            () -> { if (tp != null) tp.cycleDistance(); });
        distance.value = tp != null ? tp.distanceLabel() : "-";
        rows.add(distance);

        Row side = new Row("Side offset", Kind.VALUE,
            () -> { if (tp != null) tp.cycleSideOffset(); });
        side.value = tp != null ? tp.sideLabel() : "-";
        rows.add(side);

        Row combat = new Row("Combat center", Kind.TOGGLE,
            () -> { if (tp != null) tp.toggleCombatCentering(); });
        combat.toggled = tp != null && tp.combatCentering();
        rows.add(combat);

        Row reticle = new Row("True reticle", Kind.TOGGLE,
            () -> { if (tp != null) tp.toggleTrueAimReticle(); });
        reticle.toggled = tp != null && tp.trueAimReticle();
        rows.add(reticle);

        Row aim = new Row("Aim align", Kind.TOGGLE,
            () -> { if (tp != null) tp.toggleAimAlign(); });
        aim.toggled = tp != null && tp.aimAlign();
        rows.add(aim);

        Row aimDistance = new Row("Aim distance", Kind.VALUE,
            () -> { if (tp != null) tp.cycleAimDistance(); });
        aimDistance.value = tp != null ? tp.aimDistanceLabel() : "-";
        rows.add(aimDistance);

        Row height = new Row("Height offset", Kind.VALUE,
            () -> { if (tp != null) tp.cycleHeightOffset(); });
        height.value = tp != null ? tp.heightLabel() : "-";
        rows.add(height);

        Row fov = new Row("FOV boost", Kind.VALUE,
            () -> { if (tp != null) tp.cycleFovBoost(); });
        fov.value = tp != null ? tp.fovLabel() : "-";
        rows.add(fov);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }

    private void buildFriends() {
        FriendsModule fr = BIWMod.MODULES.get(FriendsModule.class);
        FriendManager fm = FriendManager.get();

        Row enable = new Row("Enabled", Kind.TOGGLE,
            () -> { if (fr != null) fr.setEnabled(!fr.isEnabled()); });
        enable.toggled = fr != null && fr.isEnabled();
        rows.add(enable);

        Row style = new Row("Highlight style", Kind.VALUE,
            () -> { if (fr != null) fr.cycleStyle(); });
        style.value = fr == null ? "-"
            : (fr.style() == FriendsModule.Style.DOT ? "Dot" : "Tint");
        rows.add(style);

        Row color = new Row("Color", Kind.VALUE, () -> { fm.cycleColor(); });
        color.value = "\u00a7" + fm.colorCode() + "\u25cf";
        rows.add(color);

        Row protect = new Row("Don't hit friends", Kind.TOGGLE,
            () -> { if (fr != null) fr.toggleProtectFriends(); });
        protect.toggled = fr != null && fr.protectFriends();
        rows.add(protect);

        Row count = new Row("Friends added", Kind.VALUE, null);
        count.value = String.valueOf(fm.count());
        rows.add(count);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }

    private void buildGroup() {
        GroupModule gr = BIWMod.MODULES.get(GroupModule.class);
        GroupManager gm = GroupManager.get();

        Row enable = new Row("Enabled", Kind.TOGGLE,
            () -> { if (gr != null) gr.setEnabled(!gr.isEnabled()); });
        enable.toggled = gr != null && gr.isEnabled();
        rows.add(enable);

        Row name = new Row("Team name", Kind.VALUE, null);
        name.value = gm.hasGroup() ? "[" + gm.tag() + "]" : "-";
        rows.add(name);

        Row style = new Row("Highlight style", Kind.VALUE,
            () -> { if (gr != null) gr.cycleStyle(); });
        style.value = gr == null ? "-"
            : (gr.style() == GroupModule.Style.TAG ? "Tag" : "Tint");
        rows.add(style);

        Row color = new Row("Color", Kind.VALUE, () -> { gm.cycleColor(); });
        color.value = "\u00a7" + gm.colorCode() + "\u25c6";
        rows.add(color);

        Row count = new Row("Members", Kind.VALUE, null);
        count.value = String.valueOf(gm.count());
        rows.add(count);

        rows.add(new Row("Back", Kind.BACK, () -> { page = Page.MAIN; rebuild(); }));
    }
    private String hearts(int halfHearts) {
        if (halfHearts % 2 == 0) {
            return (halfHearts / 2) + "\u2665";
        }
        return (halfHearts / 2.0) + "\u2665";
    }
    private void layout() {
        int headerH = 30;
        int contentTop = headerH + 10;
        int contentH = rows.size() * (ROW_H + ROW_GAP) + 8;
        panelH = contentTop + contentH + 8;
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(20, (height - panelH) / 2);

        int rx = panelX + 12;
        int rw = PANEL_W - 24;
        int ry = panelY + contentTop;
        for (Row r : rows) {
            r.x = rx;
            r.y = ry;
            r.w = rw;
            r.h = ROW_H;
            ry += ROW_H + ROW_GAP;
        }
    }

    // --- Rendering ------------------------------------------------------------

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        // Dim the world behind the panel.
        fill(0, 0, width, height, COL_DIM);

        // Panel border + background.
        fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, COL_PANEL_BD);
        fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, COL_PANEL);

        // Header gradient bar.
        int headerBottom = panelY + 30;
        fillGradient(panelX, panelY, panelX + PANEL_W, headerBottom, COL_HEADER_A, COL_HEADER_B);
        String title;
        if (page == Page.SOP) title = "BIW \u2022 SOP";
        else if (page == Page.AUTOSOUP) title = "BIW \u2022 Auto-soup";
        else if (page == Page.AUTOCLICKER) title = "BIW \u2022 Auto-clicker";
        else if (page == Page.THIRD_PERSON) title = "BIW \u2022 Third-person";
        else if (page == Page.FRIENDS) title = "BIW \u2022 Friends";
        else if (page == Page.GROUP) title = "BIW \u2022 Group";
        else title = "BIW \u2022 Settings";
        drawCenteredString(textRenderer, title, width / 2, panelY + 11, 0xFFFFFFFF);

        // Subtitle hint.
        String hint = null;
        if (page == Page.SOP) {
            hint = "Press 1-9 over soup: eat, drop bowl, back to sword";
        } else if (page == Page.AUTOSOUP) {
            hint = "Auto-eats soup only when worth it (saves soup)";
        } else if (page == Page.AUTOCLICKER) {
            hint = "Conservative 12-13 by default; 14 can still be risky";
        } else if (page == Page.THIRD_PERSON) {
            hint = "PvP preset: true aim reticle, combat centering";
        } else if (page == Page.FRIENDS) {
            hint = "Mark friends with a color. Use .friend add <name>";
        } else if (page == Page.GROUP) {
            hint = "Team tag on members. .group name <t>, .group add <name>";
        }
        if (hint != null) {
            int tw = textRenderer.getStringWidth(hint);
            textRenderer.draw(hint, width / 2 - tw / 2, panelY + 34, COL_SUBTLE);
        }

        // Rows.
        for (Row r : rows) {
            boolean hover = mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h;
            drawRow(r, hover);
        }

        super.render(mouseX, mouseY, partialTicks);
    }

    private void drawRow(Row r, boolean hover) {
        // Row card.
        fill(r.x - 1, r.y - 1, r.x + r.w + 1, r.y + r.h + 1, COL_ROW_BD);
        fill(r.x, r.y, r.x + r.w, r.y + r.h, hover ? COL_ROW_HOV : COL_ROW);

        int textY = r.y + (r.h - 8) / 2;

        if (r.kind == Kind.BACK) {
            // Centered back label.
            drawCenteredString(textRenderer, "\u2190 " + r.label, r.x + r.w / 2, textY, COL_TEXT);
            return;
        }

        // Left label.
        textRenderer.draw(r.label, r.x + 8, textY, COL_TEXT);

        if (r.kind == Kind.TOGGLE) {
            drawPill(r, r.toggled ? "ON" : "OFF", r.toggled ? COL_ON : COL_OFF);
        } else if (r.kind == Kind.VALUE) {
            drawPill(r, r.value != null ? r.value : "-", COL_VALUE);
        } else if (r.kind == Kind.LINK) {
            // Right chevron to indicate navigation.
            String chev = "\u203A";
            int cw = textRenderer.getStringWidth(chev);
            textRenderer.draw(chev, r.x + r.w - 10 - cw, textY, COL_SUBTLE);
        }
    }

    /** Draw a small colored pill with centered text at the row's right edge. */
    private void drawPill(Row r, String text, int color) {
        int tw = textRenderer.getStringWidth(text);
        int pillW = tw + 14;
        int pillH = 14;
        int px2 = r.x + r.w - 8;
        int px1 = px2 - pillW;
        int py1 = r.y + (r.h - pillH) / 2;
        int py2 = py1 + pillH;

        // Pill background = dim version of color; text = bright color.
        fill(px1, py1, px2, py2, dim(color, 0x55));
        fill(px1, py1, px2, py1 + 1, color);          // top accent line
        textRenderer.draw(text, px1 + 7, py1 + 3, color);
    }

    /** Apply an alpha to an opaque ARGB color. */
    private int dim(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // --- Input ----------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            for (Row r : rows) {
                if (mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h) {
                    if (r.action != null) {
                        r.action.run();
                        rebuild();
                    }
                    return;
                }
            }
        }
        // Let vanilla handle anything else (none, but keep contract).
    }

    @Override
    protected void keyPressed(char chr, int keyCode) {
        // 1 == Escape (LWJGL).
        if (keyCode == 1) {
            if (page != Page.MAIN) {
                page = Page.MAIN;
                rebuild();
            } else {
                close();
            }
            return;
        }
        super.keyPressed(chr, keyCode);
    }

    private void close() {
        if (BIWMod.CONFIG != null) {
            BIWMod.MODULES.saveAll(BIWMod.CONFIG);
        }
        if (client != null) {
            client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    private String displayName(String id) {
        if (id == null || id.isEmpty()) return "?";
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
