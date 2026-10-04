package com.biw.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    private static final String[] BIW_EMOJIS = {
        "\u263A", "\u2764", "\u2605", "\u2620", "\u26A1", "\u2600", "\u263E", "\u266B",
        "\u2714", "\u2718", "\u270C", "\u2726", "\u273F", "\u2615", "\u262F", "\u267B",
        "\u25B6", "\u25C0", "\u25B2", "\u25BC", "\u25C6", "\u25CF", "\u25A0", "\u272A"
    };
    private static final int BIW_COLS = 8;
    private static final int BIW_CELL_W = 22;
    private static final int BIW_CELL_H = 18;
    private static final int BIW_BUTTON_W = 22;
    private static final int BIW_BUTTON_H = 18;

    @Shadow
    private TextFieldWidget chatField;

    private boolean biw$emojiOpen = false;

    @Inject(method = "render(IIF)V", at = @At("TAIL"))
    private void biw$renderEmojiPicker(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        int bx = biw$buttonX();
        int by = biw$buttonY();
        boolean buttonHover = biw$inside(mouseX, mouseY, bx, by, BIW_BUTTON_W, BIW_BUTTON_H);
        DrawableHelper.fill(bx - 1, by - 1, bx + BIW_BUTTON_W + 1, by + BIW_BUTTON_H + 1, 0xDD000000);
        DrawableHelper.fill(bx, by, bx + BIW_BUTTON_W, by + BIW_BUTTON_H, buttonHover || biw$emojiOpen ? 0xCC3DD6C4 : 0xAA202028);
        String label = "\u263A";
        mc.textRenderer.draw(label, bx + (BIW_BUTTON_W - mc.textRenderer.getStringWidth(label)) / 2, by + 5, 0xFFFFFFFF);

        if (!biw$emojiOpen) return;

        int px = bx;
        int py = by - biw$panelH() - 4;
        int pw = BIW_COLS * BIW_CELL_W;
        int ph = biw$panelH();
        DrawableHelper.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xDD000000);
        DrawableHelper.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xCC3DD6C4);
        DrawableHelper.fill(px, py, px + pw, py + ph, 0xEE1B1B24);

        for (int i = 0; i < BIW_EMOJIS.length; i++) {
            int col = i % BIW_COLS;
            int row = i / BIW_COLS;
            int x = px + col * BIW_CELL_W;
            int y = py + row * BIW_CELL_H;
            boolean hover = biw$inside(mouseX, mouseY, x, y, BIW_CELL_W, BIW_CELL_H);
            if (hover) {
                DrawableHelper.fill(x, y, x + BIW_CELL_W, y + BIW_CELL_H, 0x663DD6C4);
            }
            String emoji = BIW_EMOJIS[i];
            int tw = mc.textRenderer.getStringWidth(emoji);
            mc.textRenderer.draw(emoji, x + (BIW_CELL_W - tw) / 2, y + 5, hover ? 0xFFFFC857 : 0xFFE8E8F0);
        }
    }

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void biw$mouseClicked(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button != 0) return;

        int bx = biw$buttonX();
        int by = biw$buttonY();
        if (biw$inside(mouseX, mouseY, bx, by, BIW_BUTTON_W, BIW_BUTTON_H)) {
            biw$emojiOpen = !biw$emojiOpen;
            if (chatField != null) chatField.setFocused(true);
            ci.cancel();
            return;
        }

        if (!biw$emojiOpen) return;

        int px = bx;
        int py = by - biw$panelH() - 4;
        int col = (mouseX - px) / BIW_CELL_W;
        int row = (mouseY - py) / BIW_CELL_H;
        if (col >= 0 && col < BIW_COLS && row >= 0 && row < biw$rows()) {
            int index = row * BIW_COLS + col;
            if (index >= 0 && index < BIW_EMOJIS.length && chatField != null) {
                chatField.write(BIW_EMOJIS[index]);
                chatField.setFocused(true);
                ci.cancel();
                return;
            }
        }

        if (!biw$inside(mouseX, mouseY, px, py, BIW_COLS * BIW_CELL_W, biw$panelH())) {
            biw$emojiOpen = false;
        }
    }

    private int biw$buttonX() {
        return 4;
    }

    private int biw$buttonY() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? 4 : mc.currentScreen.height - 34;
    }

    private int biw$panelH() {
        return biw$rows() * BIW_CELL_H;
    }

    private int biw$rows() {
        return (BIW_EMOJIS.length + BIW_COLS - 1) / BIW_COLS;
    }

    private boolean biw$inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
