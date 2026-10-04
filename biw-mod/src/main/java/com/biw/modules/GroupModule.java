package com.biw.modules;

import com.biw.core.GroupManager;
import com.biw.core.Module;
import com.google.gson.JsonObject;

/**
 * Group/squad highlighter settings. Members keep their real vanilla nametag;
 * the player renderer draws a small anchored group chip above it.
 */
public final class GroupModule extends Module {

    public enum Style { TAG, TINT_NAME }

    private Style style = Style.TAG;
    private boolean distantMarkers = true;
    private int markerRange = 256;

    public GroupModule() {
        super("group", true);
    }

    public Style style() {
        return style;
    }

    public void cycleStyle() {
        style = style == Style.TAG ? Style.TINT_NAME : Style.TAG;
    }

    public boolean distantMarkers() {
        return distantMarkers;
    }

    public int markerRange() {
        return markerRange;
    }

    public String markerLabel() {
        String tag = GroupManager.get().tag();
        String marker = tag.isEmpty() ? "\u25c6" : "[" + tag + "]";
        return "\u00a7" + GroupManager.get().colorCode() + marker;
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("style")) {
            try {
                style = Style.valueOf(s.get("style").getAsString());
            } catch (Exception ignored) {
            }
        }
        if (s.has("distantMarkers")) distantMarkers = s.get("distantMarkers").getAsBoolean();
        if (s.has("markerRange")) markerRange = clampRange(s.get("markerRange").getAsInt());
        GroupManager.get().load(s);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = GroupManager.get().save();
        s.addProperty("enabled", isEnabled());
        s.addProperty("style", style.name());
        s.addProperty("distantMarkers", distantMarkers);
        s.addProperty("markerRange", markerRange);
        return s;
    }

    private int clampRange(int value) {
        if (value < 32) return 32;
        if (value > 512) return 512;
        return value;
    }
}
