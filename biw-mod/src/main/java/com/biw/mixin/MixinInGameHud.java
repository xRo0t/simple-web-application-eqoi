package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.modules.ThirdPersonModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Posts a HUD render event at the end of the vanilla overlay render so BIW HUD
 * modules can draw on top.
 */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "render(F)V", at = @At("RETURN"))
    private void biw$onRenderHud(float partialTicks, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = new Window(mc);
        int w = (int) window.getScaledWidth();
        int h = (int) window.getScaledHeight();
        BIWMod.EVENTS.postRenderHud(w, h, partialTicks);
    }

    @Inject(method = "showCrosshair()Z", at = @At("HEAD"), cancellable = true)
    private void biw$hideVanillaCrosshairForTrueAim(CallbackInfoReturnable<Boolean> cir) {
        ThirdPersonModule tp = BIWMod.thirdPerson();
        if (tp != null && tp.cameraActive() && tp.trueAimReticle()) {
            cir.setReturnValue(false);
        }
    }
}
