package com.biw.mixin;

import com.biw.core.ScreenshotCleaner;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {

    @Inject(method = "render(I)V", at = @At("HEAD"), cancellable = true)
    private void biw$hideMessagesForCleanScreenshot(int tick, CallbackInfo ci) {
        if (ScreenshotCleaner.hidingMessages()) {
            ci.cancel();
        }
    }
}
