package com.biw.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private static FPS counter for the info HUD. */
@Mixin(MinecraftClient.class)
public interface AccessorMinecraftClient {

    @Accessor("currentFps")
    static int biw$getCurrentFps() {
        throw new AssertionError();
    }
}
