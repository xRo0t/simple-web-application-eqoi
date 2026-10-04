package com.biw.mixin;

import com.biw.BIWMod;
import com.biw.core.BiwSettingsScreen;
import com.biw.core.ScreenshotCleaner;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.Text;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Polls keyboard state once per client tick and forwards key states to modules.
 * Also opens the in-game BIW settings menu on a fresh press of the menu key.
 */
@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    /** Key that opens the BIW settings menu (LWJGL Right Shift). */
    private static final int MENU_KEY = Keyboard.KEY_RSHIFT;

    private static final int[] OBSERVED_KEYS = {
        Keyboard.KEY_C,
        Keyboard.KEY_V,
        Keyboard.KEY_1,
        Keyboard.KEY_2,
        Keyboard.KEY_3,
        Keyboard.KEY_4,
        Keyboard.KEY_5,
        Keyboard.KEY_6,
        Keyboard.KEY_7,
        Keyboard.KEY_8,
        Keyboard.KEY_9
    };

    private boolean biw$menuKeyWasDown = false;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void biw$pollKeys(CallbackInfo ci) {
        BIWMod.MODULES.dispatchClientTick();

        for (int i = 0; i < OBSERVED_KEYS.length; i++) {
            int key = OBSERVED_KEYS[i];
            BIWMod.EVENTS.postKey(key, Keyboard.isKeyDown(key));
        }

        biw$handleMenuKey();
    }

    private void biw$handleMenuKey() {
        boolean down = Keyboard.isKeyDown(MENU_KEY);
        boolean freshPress = down && !biw$menuKeyWasDown;
        biw$menuKeyWasDown = down;

        if (!freshPress) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // Only open from in-world with no other screen up.
        if (mc == null || mc.player == null || mc.currentScreen != null) return;
        mc.setScreen(new BiwSettingsScreen());
    }

    @Redirect(
        method = "handleKeyInput()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/ScreenshotUtils;saveScreenshot(Ljava/io/File;IILnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/text/Text;"
        )
    )
    private Text biw$saveScreenshotWithoutMessages(File runDirectory, int width, int height, Framebuffer framebuffer) {
        return ScreenshotCleaner.saveWithoutMessages(runDirectory, width, height, framebuffer);
    }
}
