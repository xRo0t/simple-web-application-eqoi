package com.biw.modules;

import com.biw.core.InputModule;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * SOP mode: pressing a hotbar number (1-9) switches to that slot, uses/eats the
 * item, drops whatever remains (the empty bowl), then switches back to the
 * previous slot (typically the sword).
 *
 * Safety check: the action only runs if the item in the pressed slot is
 * consumable (soup, food, potion) - i.e. it has a use time. If the slot holds a
 * sword or any non-consumable, the press is ignored so nothing breaks.
 *
 * Continuous items (golden apple, steak) are handled by holding right-click for
 * the full eat animation; instant items (soup) finish in a tick.
 *
 * Timing: keys are polled at the head of the client tick, before vanilla
 * switches the held slot and syncs it to the server. So a press only schedules
 * the action; the use begins next tick once the slot is synced.
 */
public final class SopModule extends InputModule {

    /** Hard cap so a stuck use can never hold the key forever. */
    private static final int MAX_USE_TICKS = 40;

    private final boolean[] keyDown = new boolean[9];

    // Settings (persisted).
    private int firstSlot = 0;
    private int lastSlot = 8;
    private boolean restorePreviousSlot = true;
    private int dropDelayTicks = 1;

    // Phase machine for the active action.
    private enum Phase { IDLE, START_USE, HOLDING, DROP_WAIT }

    private Phase phase = Phase.IDLE;
    private int actionSlot = -1;
    private int previousSlot = -1;
    private int useTicks = 0;
    private boolean sawUsing = false;
    private int dropWait = 0;

    public SopModule() {
        super("sop", false);
    }

    @Override
    public void onKey(int keyCode, boolean pressed) {
        int slot = slotForKey(keyCode);
        if (slot < 0) return;

        if (!pressed) {
            keyDown[slot] = false;
            return;
        }
        if (keyDown[slot]) return;
        keyDown[slot] = true;

        scheduleUse(slot);
    }

    private int slotForKey(int keyCode) {
        if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            return keyCode - Keyboard.KEY_1;
        }
        return -1;
    }

    /**
     * Records a use request. Ignored if an action is already running, the slot
     * is out of the configured range, or the slot does not hold a consumable
     * (so pressing a number over a sword does nothing).
     */
    private void scheduleUse(int slot) {
        if (phase != Phase.IDLE) return;
        if (slot < firstSlot || slot > lastSlot) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (slot < 0 || slot >= PlayerInventory.getHotbarSize()) return;

        ItemStack stack = mc.player.inventory.main[slot];
        if (stack == null) return;
        if (!isConsumable(stack)) return; // sword / non-food -> ignore

        actionSlot = slot;
        previousSlot = mc.player.inventory.selectedSlot;
        phase = Phase.START_USE;
    }

    /**
     * Only EAT/DRINK items qualify (soup, food, potions, milk). This excludes
     * swords and tools (UseAction.NONE), bows (BOW), and shields/blocking
     * (BLOCK) - so pressing a number over a sword does nothing.
     */
    private boolean isConsumable(ItemStack stack) {
        net.minecraft.util.UseAction action = stack.getUseAction();
        return action == net.minecraft.util.UseAction.EAT
            || action == net.minecraft.util.UseAction.DRINK;
    }

    @Override
    public void onClientTick() {
        if (phase == Phase.IDLE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null
                || mc.interactionManager == null || mc.currentScreen != null) {
            abort();
            return;
        }
        if (actionSlot < 0 || actionSlot >= PlayerInventory.getHotbarSize()) {
            abort();
            return;
        }

        switch (phase) {
            case START_USE:
                startUse(mc);
                break;
            case HOLDING:
                tickHolding(mc);
                break;
            case DROP_WAIT:
                tickDropWait(mc);
                break;
            default:
                break;
        }
    }

    private void startUse(MinecraftClient mc) {
        if (mc.player.inventory.main[actionSlot] == null) {
            abort();
            return;
        }

        mc.player.inventory.selectedSlot = actionSlot;
        // Press and hold "use item" so vanilla drives the eat/drink animation.
        setUseKey(mc, true);
        useTicks = 0;
        sawUsing = false;
        phase = Phase.HOLDING;
    }

    private void tickHolding(MinecraftClient mc) {
        // Keep us on the action slot and the use key held.
        mc.player.inventory.selectedSlot = actionSlot;
        setUseKey(mc, true);
        useTicks++;

        boolean usingNow = mc.player.isUsingItem();
        if (usingNow) {
            sawUsing = true;
        }

        // Finish when a continuous use completes (was using, now stopped), or
        // for instant items that never set the using flag once a tick passed,
        // or when we hit the safety cap.
        boolean finished = (sawUsing && !usingNow)
                || (!sawUsing && useTicks >= 2)
                || useTicks >= MAX_USE_TICKS;
        if (!finished) return;

        setUseKey(mc, false);
        dropWait = dropDelayTicks;
        phase = Phase.DROP_WAIT;
    }

    private void tickDropWait(MinecraftClient mc) {
        if (dropWait > 0) {
            dropWait--;
            return;
        }

        // Drop the leftover (empty bowl) if anything remains in the slot.
        if (mc.player.inventory.main[actionSlot] != null) {
            mc.player.inventory.selectedSlot = actionSlot;
            mc.player.dropSelectedItem(false);
        }

        // Switch back to the previous slot (the sword).
        if (restorePreviousSlot && previousSlot >= 0
                && previousSlot < PlayerInventory.getHotbarSize()
                && previousSlot != actionSlot
                && mc.player.inventory.main[previousSlot] != null) {
            mc.player.inventory.selectedSlot = previousSlot;
        }

        reset();
    }

    private void setUseKey(MinecraftClient mc, boolean pressed) {
        if (mc.options == null || mc.options.useKey == null) return;
        KeyBinding.setKeyPressed(mc.options.useKey.getCode(), pressed);
    }

    private void abort() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) setUseKey(mc, false);
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        actionSlot = -1;
        previousSlot = -1;
        useTicks = 0;
        sawUsing = false;
        dropWait = 0;
    }

    @Override
    protected void onDisable() {
        for (int i = 0; i < keyDown.length; i++) {
            keyDown[i] = false;
        }
        abort();
    }

    // --- Settings accessors used by the in-game settings screen ---------------

    public boolean restorePreviousSlot() {
        return restorePreviousSlot;
    }

    public void toggleRestorePreviousSlot() {
        restorePreviousSlot = !restorePreviousSlot;
    }

    public int dropDelayTicks() {
        return dropDelayTicks;
    }

    public void cycleDropDelay() {
        dropDelayTicks = dropDelayTicks >= 5 ? 0 : dropDelayTicks + 1;
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("firstSlot")) firstSlot = clampSlot(s.get("firstSlot").getAsInt());
        if (s.has("lastSlot")) lastSlot = clampSlot(s.get("lastSlot").getAsInt());
        if (s.has("restorePreviousSlot")) restorePreviousSlot = s.get("restorePreviousSlot").getAsBoolean();
        if (s.has("dropDelayTicks")) dropDelayTicks = clampDelay(s.get("dropDelayTicks").getAsInt());
        if (firstSlot > lastSlot) {
            int tmp = firstSlot;
            firstSlot = lastSlot;
            lastSlot = tmp;
        }
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("firstSlot", firstSlot);
        s.addProperty("lastSlot", lastSlot);
        s.addProperty("restorePreviousSlot", restorePreviousSlot);
        s.addProperty("dropDelayTicks", dropDelayTicks);
        return s;
    }

    private int clampSlot(int slot) {
        if (slot < 0) return 0;
        if (slot > 8) return 8;
        return slot;
    }

    private int clampDelay(int ticks) {
        if (ticks < 0) return 0;
        if (ticks > 5) return 5;
        return ticks;
    }
}
