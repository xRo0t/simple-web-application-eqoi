package com.biw.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface InvokerEntityRenderer {

    @Invoker("renderLabelIfPresent")
    void biw$renderLabelIfPresent(Entity entity, String label, double x, double y, double z, int maxDistance);
}
