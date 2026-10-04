package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.modules.ThirdPersonModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Two hooks:
 *  - world render end -> post a world-render event (used by Hitbox).
 *  - getFov -> apply the zoom multiplier (used by Zoom).
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    private float thirdPersonDistance;

    @Shadow
    private float lastThirdPersonDistance;

    private boolean biw$distanceOverridden = false;
    private float biw$oldThirdPersonDistance = 4.0f;
    private float biw$oldLastThirdPersonDistance = 4.0f;

    @Inject(method = "renderWorld(IFJ)V", at = @At("RETURN"))
    private void biw$onRenderWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        BIWMod.EVENTS.postRenderWorld(partialTicks);
    }

    @Inject(method = "getFov(FZ)F", at = @At("RETURN"), cancellable = true)
    private void biw$applyZoom(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        float multiplier = 1.0f;
        if (BIWMod.zoom() != null) {
            multiplier *= BIWMod.zoom().fovMultiplier();
        }
        ThirdPersonModule thirdPerson = BIWMod.thirdPerson();
        if (thirdPerson != null) {
            multiplier *= thirdPerson.fovMultiplier();
        }
        if (multiplier != 1.0f) {
            cir.setReturnValue(cir.getReturnValueF() * multiplier);
        }
    }

    @Inject(method = "transformCamera(F)V", at = @At("HEAD"))
    private void biw$setThirdPersonDistance(float partialTicks, CallbackInfo ci) {
        ThirdPersonModule thirdPerson = BIWMod.thirdPerson();
        if (thirdPerson != null && thirdPerson.cameraActive()) {
            if (!biw$distanceOverridden) {
                biw$oldThirdPersonDistance = thirdPersonDistance;
                biw$oldLastThirdPersonDistance = lastThirdPersonDistance;
                biw$distanceOverridden = true;
            }
            thirdPersonDistance = thirdPerson.distance();
            lastThirdPersonDistance = thirdPerson.distance();
        } else if (biw$distanceOverridden) {
            thirdPersonDistance = biw$oldThirdPersonDistance;
            lastThirdPersonDistance = biw$oldLastThirdPersonDistance;
            biw$distanceOverridden = false;
        }
    }

    @ModifyArgs(
        method = "transformCamera(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V",
            ordinal = 2
        )
    )
    private void biw$applyShoulderCamera(Args args) {
        ThirdPersonModule thirdPerson = BIWMod.thirdPerson();
        if (thirdPerson == null || !thirdPerson.cameraActive()) return;
        args.set(0, thirdPerson.translateX());
        args.set(1, thirdPerson.translateY());
    }

    @ModifyArgs(
        method = "transformCamera(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V",
            ordinal = 8
        )
    )
    private void biw$alignFinalCameraPitch(Args args) {
        ThirdPersonModule thirdPerson = BIWMod.thirdPerson();
        if (thirdPerson == null || !thirdPerson.cameraActive() || !thirdPerson.aimAlign()) return;
        args.set(0, ((Float) args.get(0)) + thirdPerson.convergencePitch());
    }

    @ModifyArgs(
        method = "transformCamera(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V",
            ordinal = 10
        )
    )
    private void biw$alignFinalCameraYaw(Args args) {
        ThirdPersonModule thirdPerson = BIWMod.thirdPerson();
        if (thirdPerson == null || !thirdPerson.cameraActive() || !thirdPerson.aimAlign()) return;
        args.set(0, ((Float) args.get(0)) + thirdPerson.convergenceYaw());
    }
}
