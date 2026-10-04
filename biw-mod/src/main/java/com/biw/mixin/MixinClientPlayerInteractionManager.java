package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.core.FriendManager;
import com.biw.core.GroupManager;
import com.biw.modules.FriendsModule;
import com.biw.modules.GroupModule;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Friendly-fire protection. {@code attackEntity} is the single client-side
 * entry point for every melee hit on an entity (it sends the ATTACK packet and
 * runs the local swing). If the target is a friend or a clan member, we cancel
 * at HEAD before anything is sent, so the server never registers a hit: no
 * damage, no knockback dealt to them.
 *
 * Note: damage/knockback are authoritative on the server, so the only reliable
 * way to "not hit" a friend is to never send the attack — which is exactly what
 * this does. It's purely a guard on your own outgoing action.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void biw$protectFriends(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (!(target instanceof PlayerEntity)) return;

        String name = biw$realName((PlayerEntity) target);
        if (name == null) return;

        // Clan members are always protected when the group module is on.
        GroupModule group = BIWMod.MODULES.get(GroupModule.class);
        if (group != null && group.isEnabled() && GroupManager.get().isMember(name)) {
            ci.cancel();
            return;
        }

        // Friends are protected only when the toggle is on.
        FriendsModule friends = BIWMod.MODULES.get(FriendsModule.class);
        if (friends != null && friends.isEnabled() && friends.protectFriends()
                && FriendManager.get().isFriend(name)) {
            ci.cancel();
        }
    }

    private String biw$realName(PlayerEntity target) {
        try {
            if (target.getGameProfile() != null && target.getGameProfile().getName() != null) {
                return target.getGameProfile().getName();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
