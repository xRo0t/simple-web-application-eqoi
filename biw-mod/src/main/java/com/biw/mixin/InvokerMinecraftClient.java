package com.biw.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface InvokerMinecraftClient {
    @Invoker("doAttack")
    void biw$doAttack();
}
