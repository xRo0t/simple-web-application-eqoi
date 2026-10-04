package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.core.FriendManager;
import com.biw.core.GroupManager;
import com.biw.modules.FriendsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side {@code .friend} command handler. Intercepts outgoing chat so the
 * player can manage their cosmetic friend list without anything being sent to
 * the server:
 *
 *   .friend add <name>
 *   .friend remove <name>
 *   .friend list
 *   .friend color
 *
 * Any non-matching message passes through to vanilla untouched.
 */
@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void biw$handleFriendCommand(String message, CallbackInfo ci) {
        if (message == null) return;
        String msg = message.trim();
        String lower = msg.toLowerCase();

        if (lower.startsWith(".friend")) {
            ci.cancel();
            handleFriend(msg);
        } else if (lower.startsWith(".group") || lower.startsWith(".g ") || lower.equals(".g")) {
            ci.cancel();
            handleGroup(msg);
        }
    }

    private void handleFriend(String msg) {
        String[] parts = msg.split("\\s+");
        FriendManager fm = FriendManager.get();

        if (parts.length == 1) {
            info("Usage: .friend add|remove|list|color <name>");
            return;
        }

        String sub = parts[1].toLowerCase();
        switch (sub) {
            case "add":
                if (parts.length < 3) { info("Usage: .friend add <name>"); return; }
                if (fm.add(parts[2])) info("Added \u00a7a" + parts[2] + "\u00a7r to friends.");
                else info("\u00a7e" + parts[2] + "\u00a7r is already a friend.");
                save();
                break;
            case "remove":
            case "rem":
            case "del":
                if (parts.length < 3) { info("Usage: .friend remove <name>"); return; }
                if (fm.remove(parts[2])) info("Removed \u00a7c" + parts[2] + "\u00a7r from friends.");
                else info("\u00a7e" + parts[2] + "\u00a7r is not a friend.");
                save();
                break;
            case "list":
                if (fm.count() == 0) info("No friends added yet.");
                else info("Friends (" + fm.count() + "): \u00a7a" + String.join("\u00a7r, \u00a7a", fm.names()));
                break;
            case "color":
            case "colour":
                fm.cycleColor();
                info("Friend color set to \u00a7" + fm.colorCode() + "this\u00a7r.");
                save();
                break;
            default:
                info("Unknown subcommand. Use add|remove|list|color.");
                break;
        }
    }

    private void handleGroup(String msg) {
        String[] parts = msg.split("\\s+");
        GroupManager gm = GroupManager.get();

        if (parts.length == 1) {
            info("Usage: .group name|add|remove|list|color|disband <arg>");
            return;
        }

        String sub = parts[1].toLowerCase();
        switch (sub) {
            case "name":
                if (parts.length < 3) { info("Usage: .group name <team name>"); return; }
                gm.setGroupName(parts[2]);
                info("Group name set to \u00a7" + gm.colorCode() + gm.groupName() + "\u00a7r (tag [" + gm.tag() + "]).");
                save();
                break;
            case "add":
                if (parts.length < 3) { info("Usage: .group add <name>"); return; }
                if (gm.add(parts[2])) info("Added \u00a7" + gm.colorCode() + parts[2] + "\u00a7r to the group.");
                else info("\u00a7e" + parts[2] + "\u00a7r is already in the group.");
                save();
                break;
            case "remove":
            case "rem":
            case "del":
                if (parts.length < 3) { info("Usage: .group remove <name>"); return; }
                if (gm.remove(parts[2])) info("Removed \u00a7c" + parts[2] + "\u00a7r from the group.");
                else info("\u00a7e" + parts[2] + "\u00a7r is not in the group.");
                save();
                break;
            case "list":
                if (gm.count() == 0) {
                    info("Group is empty.");
                } else {
                    String label = gm.hasGroup() ? gm.groupName() + " " : "";
                    info("Group " + label + "(" + gm.count() + "): \u00a7" + gm.colorCode()
                        + String.join("\u00a7r, \u00a7" + gm.colorCode(), gm.members()));
                }
                break;
            case "color":
            case "colour":
                gm.cycleColor();
                info("Group color set to \u00a7" + gm.colorCode() + "this\u00a7r.");
                save();
                break;
            case "disband":
            case "clear":
                gm.disband();
                info("Group disbanded.");
                save();
                break;
            default:
                info("Unknown subcommand. Use name|add|remove|list|color|disband.");
                break;
        }
    }

    private void save() {
        if (BIWMod.CONFIG != null) BIWMod.MODULES.saveAll(BIWMod.CONFIG);
    }

    private void info(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.sendMessage(new net.minecraft.text.LiteralText("\u00a7b[BIW] \u00a7r" + text));
    }
}
