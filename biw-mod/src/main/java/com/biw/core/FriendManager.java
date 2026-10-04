package com.biw.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Cosmetic friend list. Purely client-side and visual: it marks names you've
 * added so you can spot teammates/friends in a crowd. It grants no combat or
 * gameplay advantage — it only changes how a nametag looks for you.
 *
 * Names are stored lower-cased for case-insensitive matching. The accent color
 * (used for the dot/label tint) is shared across all friends and persisted.
 */
public final class FriendManager {

    private static final FriendManager INSTANCE = new FriendManager();

    /** A small palette the user can cycle through, ARGB (opaque). */
    public static final int[] PALETTE = {
        0xFF35D07F, // green
        0xFF1FB6FF, // blue
        0xFFFFC857, // amber
        0xFFE0556B, // pink/red
        0xFF8B5CF6, // purple
        0xFF3DD6C4, // teal
    };

    /**
     * Nametags are drawn from strings that only honor the 16 legacy {@code §}
     * color codes, so each palette entry maps to the closest legacy code for
     * the in-world marker.
     */
    private static final char[] PALETTE_CODES = {
        'a', // green
        'b', // aqua
        'e', // yellow
        'c', // red
        'd', // light purple
        '3', // dark aqua
    };

    private final Set<String> friends = new LinkedHashSet<>();
    private int colorIndex = 0;

    private FriendManager() {
    }

    public static FriendManager get() {
        return INSTANCE;
    }

    // --- Membership -----------------------------------------------------------

    public boolean isFriend(String name) {
        if (name == null || name.isEmpty()) return false;
        return friends.contains(normalize(name));
    }

    /** @return true if newly added, false if it was already present. */
    public boolean add(String name) {
        if (name == null || name.isEmpty()) return false;
        return friends.add(normalize(name));
    }

    /** @return true if removed, false if it wasn't present. */
    public boolean remove(String name) {
        if (name == null || name.isEmpty()) return false;
        return friends.remove(normalize(name));
    }

    public Set<String> names() {
        return new LinkedHashSet<>(friends);
    }

    public int count() {
        return friends.size();
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // --- Rendering helper -----------------------------------------------------

    /**
     * Given the raw nametag string the game is about to draw, return a decorated
     * version if it belongs to a friend, otherwise the original unchanged.
     *
     * @param raw      the nametag text (may contain § codes / team coloring)
     * @param tintName when true, recolor the whole name; otherwise prefix a dot
     */
    public String decorate(String raw, boolean tintName) {
        if (raw == null || raw.isEmpty()) return raw;
        // Strip any existing § codes only to test the plain name for a match;
        // the displayed string keeps its original formatting.
        String plain = stripCodes(raw);
        if (!isFriend(plain)) return raw;
        return applyMarker(raw, tintName);
    }

    /**
     * Like {@link #decorate} but the friend check uses a known real name (e.g.
     * the player's GameProfile name) instead of the displayed label, which on
     * many servers carries rank/team prefixes that would break a name match.
     * The displayed {@code raw} string is what gets decorated.
     */
    public String decorateFor(String realName, String raw, boolean tintName) {
        if (raw == null || raw.isEmpty()) return raw;
        if (!isFriend(realName)) return raw;
        return applyMarker(raw, tintName);
    }

    private String applyMarker(String raw, boolean tintName) {
        char code = colorCode();
        if (tintName) {
            return "\u00a7" + code + stripCodes(raw);
        }
        // Colored dot, then reset so the original name color is preserved.
        return "\u00a7" + code + "\u25cf\u00a7r " + raw;
    }

    /** Public access to § code stripping for sibling managers. */
    public String stripCodesPublic(String s) {
        return stripCodes(s);
    }

    private String stripCodes(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++; // skip the code char too
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // --- Color ----------------------------------------------------------------

    public int color() {
        return PALETTE[colorIndex];
    }

    /** Legacy {@code §} color code matching the current accent (for nametags). */
    public char colorCode() {
        return PALETTE_CODES[colorIndex];
    }

    /** Legacy {@code §} code for an arbitrary palette index (shared by groups). */
    public static char paletteCode(int index) {
        if (index < 0 || index >= PALETTE_CODES.length) return 'f';
        return PALETTE_CODES[index];
    }

    public void cycleColor() {
        colorIndex = (colorIndex + 1) % PALETTE.length;
    }

    // --- Persistence ----------------------------------------------------------

    public void load(JsonObject s) {
        if (s == null) return;
        friends.clear();
        if (s.has("friends") && s.get("friends").isJsonArray()) {
            JsonArray arr = s.getAsJsonArray("friends");
            for (JsonElement e : arr) {
                try {
                    add(e.getAsString());
                } catch (Exception ignored) {
                }
            }
        }
        if (s.has("colorIndex")) {
            int idx = s.get("colorIndex").getAsInt();
            if (idx >= 0 && idx < PALETTE.length) colorIndex = idx;
        }
    }

    public JsonObject save() {
        JsonObject s = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String f : friends) arr.add(new JsonPrimitive(f));
        s.add("friends", arr);
        s.addProperty("colorIndex", colorIndex);
        return s;
    }
}
