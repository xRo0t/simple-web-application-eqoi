package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.core.FriendManager;
import com.biw.modules.FriendsModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Cosmetic friend highlight. Hooks the nametag draw and, if the name belongs to
 * a friend, decorates the label string (colored dot prefix or full tint) before
 * the game renders it. Purely visual — no behavior or combat change.
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @ModifyVariable(
        method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private String biw$decorateFriendLabel(String name, Entity entity) {
        FriendsModule mod = BIWMod.MODULES.get(FriendsModule.class);
        if (mod == null || !mod.isEnabled()) return name;
        boolean tint = mod.style() == FriendsModule.Style.TINT_NAME;
        return FriendManager.get().decorate(name, tint);
    }
}
