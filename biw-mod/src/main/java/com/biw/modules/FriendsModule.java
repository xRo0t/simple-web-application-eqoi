package com.biw.modules;

import com.biw.core.FriendManager;
import com.biw.core.Module;
import com.google.gson.JsonObject;

/**
 * Cosmetic friends highlighter. When enabled, friends added via {@code .friend}
 * commands (or the settings menu) get a colored dot next to their in-world
 * nametag so you can spot them at a glance.
 *
 * This module holds only the enable flag and the highlight style; the actual
 * name list lives in {@link FriendManager}. It does nothing on tick — the work
 * happens in the nametag render mixin, gated on {@link #isEnabled()}.
 */
public final class FriendsModule extends Module {

    /** Where to put the marker relative to the name. */
    public enum Style { DOT, TINT_NAME }

    private Style style = Style.DOT;

    /**
     * When true, melee attacks on a friend are cancelled client-side so you
     * can't accidentally hit them (no damage, no knockback dealt). Also covers
     * clan members. Purely client-side: it simply suppresses the attack packet.
     */
    private boolean protectFriends = true;

    public FriendsModule() {
        super("friends", true);
    }

    public Style style() {
        return style;
    }

    public void cycleStyle() {
        style = style == Style.DOT ? Style.TINT_NAME : Style.DOT;
    }

    public boolean protectFriends() {
        return protectFriends;
    }

    public void toggleProtectFriends() {
        protectFriends = !protectFriends;
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
        if (s.has("protectFriends")) protectFriends = s.get("protectFriends").getAsBoolean();
        // The friend list + color live in the shared manager.
        FriendManager.get().load(s);
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = FriendManager.get().save();
        s.addProperty("enabled", isEnabled());
        s.addProperty("style", style.name());
        s.addProperty("protectFriends", protectFriends);
        return s;
    }
}
