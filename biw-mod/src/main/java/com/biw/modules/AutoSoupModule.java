package com.biw.modules;

import com.biw.core.Module;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Smart auto-soup for soup-PvP. Goals: never waste soup on chip damage, never
 * die holding a full bowl, and adapt to the server instead of relying on a
 * hand-tuned number.
 *
 * Intelligence layers:
 *
 *  1. Learned heal amount. We don't assume soup heals exactly 6. We measure the
 *     real effective-health gain right after each eat and keep a running
 *     estimate ({@code learnedHeal}). The eat decision uses that estimate so it
 *     auto-tunes to the server (3-heart soups, 3.5-heart, etc.).
 *
 *  2. Damage prediction. We track effective-health velocity (loss per tick,
 *     smoothed). In an active fight health drops fast, so we eat earlier; when
 *     stable we wait longer. This is a small look-ahead: predicted health a few
 *     ticks out must clear the safety floor.
 *
 *  3. Frugal-but-safe gate. Normal eat only when the missing amount is at least
 *     the (learned) heal minus a small overheal allowance, so almost no healing
 *     is wasted. An emergency override eats immediately near death regardless of
 *     waste.
 *
 *  4. Reactive timing. No fixed cooldown between eats; the only lockout is while
 *     an eat is in progress, plus a tiny post-eat settle so we read the new
 *     health before deciding again.
 *
 * Everything runs on the client tick; no threads. It switches to a soup slot,
 * uses it, drops the empty bowl, and returns to the previous slot.
 */
public final class AutoSoupModule extends Module {

    private static final int MAX_USE_TICKS = 30;
    private static final float DEFAULT_HEAL = 6f;       // half-hearts (3 hearts)
    private static final int LOOKAHEAD_TICKS = 3;       // base damage prediction window
    private static final int POST_EAT_SETTLE_TICKS = 2; // read health after eat

    private enum Phase { IDLE, USING, DROP_WAIT, SETTLE }

    // Settings (persisted, half-hearts unless noted).
    private int emergencyHealth = 6;   // always eat at/below this effective hp
    private int overhealAllowance = 2; // permitted wasted healing (half-hearts)
    private boolean predictDamage = true;
    private int dropDelayTicks = 1;

    /**
     * Combat aggressiveness 0-3. Higher values widen the damage look-ahead so
     * soup is eaten earlier in a fast fight (more reactive, slightly less
     * frugal). 0 = off (base behavior).
     */
    private int aggressiveness = 2;

    // Learned soup heal (effective-health gain), seeded with the vanilla value.
    private float learnedHeal = DEFAULT_HEAL;
    private int healSamples = 0;

    // Health tracking.
    private float lastEffective = -1f;
    private float velocity = 0f;       // smoothed loss/tick (positive = losing)

    // Eat bookkeeping.
    private Phase phase = Phase.IDLE;
    private int soupSlot = -1;
    private int previousSlot = -1;
    private int useTicks = 0;
    private boolean sawUsing = false;
    private int dropWait = 0;
    private int settle = 0;
    private float preEatEffective = 0f;

    public AutoSoupModule() {
        super("autosoup", false);
    }

    @Override
    public void onClientTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null
                || mc.interactionManager == null || mc.options == null) {
            return;
        }
        if (mc.currentScreen != null) {
            if (phase != Phase.IDLE) abort(mc);
            updateHealthTracking(mc); // keep tracking so velocity stays sane
            return;
        }

        updateHealthTracking(mc);

        switch (phase) {
            case IDLE:
                maybeStartEat(mc);
                break;
            case USING:
                tickUsing(mc);
                break;
            case DROP_WAIT:
                tickDropWait(mc);
                break;
            case SETTLE:
                tickSettle(mc);
                break;
            default:
                break;
        }
    }

    // --- Health model ---------------------------------------------------------

    private float effectiveHealth(MinecraftClient mc) {
        return mc.player.getHealth() + mc.player.getAbsorption();
    }

    private float maxEffective(MinecraftClient mc) {
        float max = mc.player.getMaxHealth();
        float eff = effectiveHealth(mc);
        // Absorption can exceed max; cap "missing" at zero by raising max.
        return Math.max(max, eff);
    }

    private void updateHealthTracking(MinecraftClient mc) {
        float eff = effectiveHealth(mc);
        if (lastEffective < 0f) {
            lastEffective = eff;
            return;
        }
        float delta = lastEffective - eff; // positive = health lost this tick
        // Exponential smoothing so a single spike doesn't dominate.
        velocity = velocity * 0.6f + delta * 0.4f;
        if (velocity < 0f) velocity = 0f; // only care about losses for prediction
        lastEffective = eff;
    }

    // --- Decision -------------------------------------------------------------

    private void maybeStartEat(MinecraftClient mc) {
        float effective = effectiveHealth(mc);
        if (effective <= 0f) return; // dead/respawning

        float missing = maxEffective(mc) - effective;
        if (missing <= 0f) return;   // already full

        // Predicted health a few ticks out, given current loss velocity. A
        // higher aggressiveness extends the look-ahead so we eat earlier when
        // trading hits fast; the frugal gate below still prevents wasted soup.
        float predicted = effective;
        if (predictDamage) {
            int lookAhead = LOOKAHEAD_TICKS + aggressiveness * 2;
            predicted = effective - velocity * lookAhead;
        }

        // Emergency is based on ACTUAL current health only: you're about to die,
        // so eat regardless of waste. Prediction must never trigger emergency,
        // otherwise a damage spike in a fight makes us eat at near-full health
        // and burn through the whole hotbar.
        boolean emergency = effective <= emergencyHealth;

        // Frugal gate: only eat when we'd recover most of a soup's heal, i.e.
        // missing is within `overhealAllowance` of the learned heal or more.
        // missing >= learnedHeal - allowance  => waste <= allowance.
        // Prediction is folded in here (not into emergency): we use the larger
        // of current/predicted missing so we can eat a little earlier in a
        // fight, but still only when the heal would NOT be wasted.
        //
        // Guard against a degenerate threshold: if the allowance is >= the
        // learned heal the raw threshold goes to zero/negative and we'd eat on
        // every single hit. Clamp it so we always require losing a meaningful
        // chunk before a non-emergency eat.
        float threshold = learnedHeal - overhealAllowance;
        float minThreshold = Math.max(1f, learnedHeal * 0.5f);
        if (threshold < minThreshold) threshold = minThreshold;

        float missingPredicted = maxEffective(mc) - predicted;
        float effectiveMissing = Math.max(missing, missingPredicted);
        boolean worthIt = effectiveMissing >= threshold;

        if (!emergency && !worthIt) return;

        int slot = findSoupHotbarSlot(mc);
        if (slot < 0) return;

        // Begin eating.
        previousSlot = mc.player.inventory.selectedSlot;
        soupSlot = slot;
        preEatEffective = effective;
        mc.player.inventory.selectedSlot = slot;
        setUseKey(mc, true);
        useTicks = 0;
        sawUsing = false;
        phase = Phase.USING;
    }

    private void tickUsing(MinecraftClient mc) {
        if (mc.player.inventory.main[soupSlot] == null) {
            setUseKey(mc, false);
            scheduleDrop();
            return;
        }

        mc.player.inventory.selectedSlot = soupSlot;
        setUseKey(mc, true);
        useTicks++;

        boolean usingNow = mc.player.isUsingItem();
        if (usingNow) sawUsing = true;

        boolean finished = (sawUsing && !usingNow)
                || (!sawUsing && useTicks >= 3)
                || useTicks >= MAX_USE_TICKS;
        if (!finished) return;

        setUseKey(mc, false);
        scheduleDrop();
    }

    private void scheduleDrop() {
        dropWait = dropDelayTicks;
        phase = Phase.DROP_WAIT;
    }

    private void tickDropWait(MinecraftClient mc) {
        if (dropWait > 0) {
            dropWait--;
            return;
        }

        ItemStack leftover = mc.player.inventory.main[soupSlot];
        if (leftover != null && leftover.getItem() == Items.BOWL) {
            mc.player.inventory.selectedSlot = soupSlot;
            mc.player.dropSelectedItem(false);
        }

        if (previousSlot >= 0 && previousSlot < PlayerInventory.getHotbarSize()
                && previousSlot != soupSlot
                && mc.player.inventory.main[previousSlot] != null) {
            mc.player.inventory.selectedSlot = previousSlot;
        }

        settle = POST_EAT_SETTLE_TICKS;
        phase = Phase.SETTLE;
    }

    /**
     * After eating, wait a couple ticks for the heal to apply, then learn the
     * real heal amount from the effective-health gain.
     */
    private void tickSettle(MinecraftClient mc) {
        if (settle > 0) {
            settle--;
            return;
        }

        float gain = effectiveHealth(mc) - preEatEffective;
        if (gain > 0.5f && gain < 20f) {
            // Running average; weight recent samples a bit more.
            if (healSamples == 0) {
                learnedHeal = gain;
            } else {
                learnedHeal = learnedHeal * 0.7f + gain * 0.3f;
            }
            if (healSamples < 1000) healSamples++;
        }

        reset();
    }

    // --- Helpers --------------------------------------------------------------

    private int findSoupHotbarSlot(MinecraftClient mc) {
        ItemStack[] main = mc.player.inventory.main;
        int size = PlayerInventory.getHotbarSize();
        for (int i = 0; i < size && i < main.length; i++) {
            ItemStack stack = main[i];
            if (stack != null && isSoup(stack.getItem())) return i;
        }
        return -1;
    }

    private boolean isSoup(Item item) {
        return item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW;
    }

    private void setUseKey(MinecraftClient mc, boolean pressed) {
        if (mc.options == null || mc.options.useKey == null) return;
        KeyBinding.setKeyPressed(mc.options.useKey.getCode(), pressed);
    }

    private void abort(MinecraftClient mc) {
        setUseKey(mc, false);
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        soupSlot = -1;
        previousSlot = -1;
        useTicks = 0;
        sawUsing = false;
        dropWait = 0;
        settle = 0;
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) setUseKey(mc, false);
        reset();
        // Keep learnedHeal across toggles; reset transient tracking.
        lastEffective = -1f;
        velocity = 0f;
    }

    // --- Settings accessors for the in-game UI --------------------------------

    public int emergencyHealth() {
        return emergencyHealth;
    }

    public void cycleEmergencyHealth() {
        emergencyHealth += 2;
        if (emergencyHealth > 12) emergencyHealth = 2;
    }

    public int overhealAllowance() {
        return overhealAllowance;
    }

    public void cycleOverhealAllowance() {
        overhealAllowance += 1;
        if (overhealAllowance > 6) overhealAllowance = 0;
    }

    public boolean predictDamage() {
        return predictDamage;
    }

    public void togglePredictDamage() {
        predictDamage = !predictDamage;
    }

    public int aggressiveness() {
        return aggressiveness;
    }

    public void cycleAggressiveness() {
        aggressiveness = aggressiveness >= 3 ? 0 : aggressiveness + 1;
    }

    /** Learned soup heal in half-hearts (for display). */
    public float learnedHeal() {
        return learnedHeal;
    }

    @Override
    public void loadConfig(JsonObject s) {
        if (s == null) return;
        if (s.has("enabled")) setEnabled(s.get("enabled").getAsBoolean());
        if (s.has("emergencyHealth")) emergencyHealth = clamp(s.get("emergencyHealth").getAsInt(), 0, 20);
        if (s.has("overhealAllowance")) overhealAllowance = clamp(s.get("overhealAllowance").getAsInt(), 0, 10);
        if (s.has("predictDamage")) predictDamage = s.get("predictDamage").getAsBoolean();
        if (s.has("dropDelayTicks")) dropDelayTicks = clamp(s.get("dropDelayTicks").getAsInt(), 0, 5);
        if (s.has("aggressiveness")) aggressiveness = clamp(s.get("aggressiveness").getAsInt(), 0, 3);
        if (s.has("learnedHeal")) learnedHeal = s.get("learnedHeal").getAsFloat();
    }

    @Override
    public JsonObject saveConfig() {
        JsonObject s = new JsonObject();
        s.addProperty("enabled", isEnabled());
        s.addProperty("emergencyHealth", emergencyHealth);
        s.addProperty("overhealAllowance", overhealAllowance);
        s.addProperty("predictDamage", predictDamage);
        s.addProperty("dropDelayTicks", dropDelayTicks);
        s.addProperty("aggressiveness", aggressiveness);
        s.addProperty("learnedHeal", learnedHeal);
        return s;
    }

    private int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
