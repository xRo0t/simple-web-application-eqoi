package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.core.FriendManager;
import com.biw.core.GroupManager;
import com.biw.modules.FriendsModule;
import com.biw.modules.GroupModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player nametags are drawn by {@link PlayerEntityRenderer}'s own label method,
 * which overrides the base {@code EntityRenderer} one with a different
 * signature, so {@link MixinEntityRenderer} never fires for players. This mixin
 * decorates that player-specific label string the same way (colored dot / tint).
 *
 * Signature: renderLabel(AbstractClientPlayerEntity, double x, double y,
 * double z, String name, float, double). The name argument sits at LVT index 8
 * because each preceding double occupies two slots.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer {

    @ModifyVariable(
        method = "method_5185(Lnet/minecraft/client/network/AbstractClientPlayerEntity;DDDLjava/lang/String;FD)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 8
    )
    private String biw$decorateFriendLabel(String name, AbstractClientPlayerEntity player) {
        // Resolve the player's real profile name once; server labels often
        // carry rank/team prefixes that would break a plain name match.
        String realName = name;
        try {
            if (player != null && player.getGameProfile() != null
                    && player.getGameProfile().getName() != null) {
                realName = player.getGameProfile().getName();
            }
        } catch (Throwable ignored) {
        }

        // Clan/group members: decorate the near nametag with the group tag or
        // tint. The matching far-range, through-wall copy is drawn from the
        // render hook below, so near and far look identical.
        GroupModule group = BIWMod.MODULES.get(GroupModule.class);
        if (group != null && group.isEnabled() && GroupManager.get().isMember(realName)) {
            boolean tintGroup = group.style() == GroupModule.Style.TINT_NAME;
            return GroupManager.get().applyMarker(name, tintGroup);
        }

        FriendsModule mod = BIWMod.MODULES.get(FriendsModule.class);
        if (mod == null || !mod.isEnabled()) return name;
        boolean tint = mod.style() == FriendsModule.Style.TINT_NAME;
        return FriendManager.get().decorateFor(realName, name, tint);
    }

    /**
     * Long-range, through-wall clan nametag.
     *
     * Vanilla only draws a player's name within 64 blocks (32 sneaking) —
     * {@code LivingEntityRenderer} hard gates it with
     * {@code if (squaredDistance >= h*h) return}. To make a clan member's name
     * visible from far and behind terrain, we hook the player {@code render}
     * method (called for every player out to entity view distance) and draw the
     * exact same decorated name ourselves.
     *
     * To avoid drawing it twice up close, we only draw past vanilla's own range;
     * inside 64 blocks the normal nametag (decorated above) already shows. We
     * pass the camera-relative x/y/z straight through — {@code renderLabelIfPresent}
     * adds the entity height + 0.5 itself, so the name sits exactly where the
     * vanilla one does, not inside the model. It also disables the depth test,
     * giving the through-wall visibility.
     */
    @Inject(
        method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;DDDFF)V",
        at = @At("HEAD")
    )
    private void biw$renderClanNameFar(AbstractClientPlayerEntity player, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (player == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && player == mc.player) return; // never mark yourself

        GroupModule group = BIWMod.MODULES.get(GroupModule.class);
        if (group == null || !group.isEnabled() || !group.distantMarkers()) return;

        String realName = realName(player, null);
        if (realName == null || !GroupManager.get().isMember(realName)) return;

        // Camera-relative offset magnitude == distance from the camera.
        double distSq = x * x + y * y + z * z;
        int range = group.markerRange();
        if (distSq > (double) range * range) return; // past our own range

        // Only draw beyond vanilla's nametag range; inside it the normal
        // (decorated) nametag already renders, so drawing here too would
        // double up. Vanilla uses 64 blocks, 32 while sneaking.
        double vanilla = player.isSneaking() ? 32.0 : 64.0;
        if (distSq < vanilla * vanilla) return;

        // Same decorated name string the near nametag uses.
        String display = player.getName().asFormattedString();
        boolean tint = group.style() == GroupModule.Style.TINT_NAME;
        String label = GroupManager.get().applyMarker(display, tint);

        ((InvokerEntityRenderer) this).biw$renderLabelIfPresent(player, label, x, y, z, range);
    }

    private String realName(AbstractClientPlayerEntity player, String fallback) {
        try {
            if (player != null && player.getGameProfile() != null
                    && player.getGameProfile().getName() != null) {
                return player.getGameProfile().getName();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
