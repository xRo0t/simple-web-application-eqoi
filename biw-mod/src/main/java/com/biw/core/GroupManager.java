package com.biw.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Cosmetic squad/group. Like {@link FriendManager} but represents a named team
 * with its own accent color, so you can tell teammates apart from individually
 * marked friends. Names are stored lower-cased for case-insensitive matching.
 */
public final class GroupManager {

    private static final GroupManager INSTANCE = new GroupManager();

    private String groupName = "";
    private final Set<String> members = new LinkedHashSet<>();
    private int colorIndex = 5; // default teal, distinct from friend default

    private GroupManager() {
    }

    public static GroupManager get() {
        return INSTANCE;
    }

    // --- Group identity -------------------------------------------------------

    public String groupName() {
        return groupName;
    }

    public void setGroupName(String name) {
        this.groupName = name == null ? "" : name.trim();
    }

    public boolean hasGroup() {
        return !groupName.isEmpty();
    }

    /** Tag shown before a member name, e.g. "SQ" -> "[SQ]". Empty if no name. */
    public String tag() {
        if (groupName.isEmpty()) return "";
        String t = groupName.length() > 3 ? groupName.substring(0, 3) : groupName;
        return t.toUpperCase(Locale.ROOT);
    }

    // --- Membership -----------------------------------------------------------

    public boolean isMember(String name) {
        if (name == null || name.isEmpty()) return false;
        return members.contains(normalize(name));
    }

    public boolean add(String name) {
        if (name == null || name.isEmpty()) return false;
        return members.add(normalize(name));
    }

    public boolean remove(String name) {
        if (name == null || name.isEmpty()) return false;
        return members.remove(normalize(name));
    }

    public Set<String> members() {
        return new LinkedHashSet<>(members);
    }

    public int count() {
        return members.size();
    }

    public void disband() {
        members.clear();
        groupName = "";
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // --- Color ----------------------------------------------------------------

    public int color() {
        return FriendManager.PALETTE[colorIndex];
    }

    public char colorCode() {
        return FriendManager.paletteCode(colorIndex);
    }

    public void cycleColor() {
        colorIndex = (colorIndex + 1) % FriendManager.PALETTE.length;
    }

    // --- Rendering helper -----------------------------------------------------

    /**
     * Decorate a teammate's displayed nametag with the group tag + color. The
     * friend check is done by the caller (using the real profile name); this
     * only formats the displayed string.
     *
     * @param raw      the displayed nametag (may carry server rank/team codes)
     * @param tintName when true recolor the whole name, else prefix a tag chip
     */
    public String applyMarker(String raw, boolean tintName) {
        if (raw == null) return null;
        char code = colorCode();
        if (tintName) {
            return "\u00a7" + code + FriendManager.get().stripCodesPublic(raw);
        }
        String tag = tag();
        if (tag.isEmpty()) {
            return "\u00a7" + code + "\u25c6\u00a7r " + raw; // diamond marker
        }
        return "\u00a7" + code + "[" + tag + "]\u00a7r " + raw;
    }

    // --- Persistence ----------------------------------------------------------

    public void load(JsonObject s) {
        if (s == null) return;
        members.clear();
        if (s.has("groupName")) groupName = s.get("groupName").getAsString();
        if (s.has("members") && s.get("members").isJsonArray()) {
            JsonArray arr = s.getAsJsonArray("members");
            for (JsonElement e : arr) {
                try {
                    add(e.getAsString());
                } catch (Exception ignored) {
                }
            }
        }
        if (s.has("colorIndex")) {
            int idx = s.get("colorIndex").getAsInt();
            if (idx >= 0 && idx < FriendManager.PALETTE.length) colorIndex = idx;
        }
    }

    public JsonObject save() {
        JsonObject s = new JsonObject();
        s.addProperty("groupName", groupName);
        JsonArray arr = new JsonArray();
        for (String m : members) arr.add(new JsonPrimitive(m));
        s.add("members", arr);
        s.addProperty("colorIndex", colorIndex);
        return s;
    }
}
