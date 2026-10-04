package com.biw.mixin;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private hitbox-debug flag so the Hitbox module can toggle it. */
@Mixin(EntityRenderDispatcher.class)
public interface AccessorEntityRenderDispatcher {

    @Accessor("renderHitboxes")
    void biw$setRenderHitboxes(boolean value);
}
