package com.biw;

import com.biw.core.Config;
import com.biw.core.EventBus;
import com.biw.core.ModuleManager;
import com.biw.modules.HitboxModule;
import com.biw.modules.InfoHudModule;
import com.biw.modules.CpsModule;
import com.biw.modules.KeystrokesModule;
import com.biw.modules.AutoSoupModule;
import com.biw.modules.AutoClickerModule;
import com.biw.modules.SopModule;
import com.biw.modules.ZoomModule;
import com.biw.modules.FriendsModule;
import com.biw.modules.GroupModule;
import com.biw.modules.ThirdPersonModule;
import com.biw.modules.ThirdPersonReticleModule;
import net.fabricmc.api.ClientModInitializer;

/**
 * BIW Mod entry point. Keeps initialization minimal and allocation-free at
 * runtime: modules only observe state and render. No threads, timers, or
 * network I/O are created here.
 */
public final class BIWMod implements ClientModInitializer {

    /** Shared event bus used by mixin hooks to notify modules. */
    public static final EventBus EVENTS = new EventBus();

    /** Module registry. */
    public static final ModuleManager MODULES = new ModuleManager();

    /** Persistent JSON config. */
    public static Config CONFIG;

    private static ZoomModule zoom;
    private static ThirdPersonModule thirdPerson;

    @Override
    public void onInitializeClient() {
        CONFIG = Config.load();

        // Register modules. Order is not significant.
        MODULES.register(new HitboxModule());
        MODULES.register(new InfoHudModule());
        MODULES.register(new CpsModule());
        MODULES.register(new KeystrokesModule());
        MODULES.register(new AutoSoupModule());
        MODULES.register(new AutoClickerModule());
        MODULES.register(new SopModule());
        MODULES.register(new FriendsModule());
        MODULES.register(new GroupModule());
        thirdPerson = new ThirdPersonModule();
        MODULES.register(thirdPerson);
        MODULES.register(new ThirdPersonReticleModule());
        zoom = new ZoomModule();
        MODULES.register(zoom);

        MODULES.loadAll(CONFIG);
    }

    /** Direct accessor used by the FOV mixin hot path (avoids map lookup). */
    public static ZoomModule zoom() {
        return zoom;
    }

    public static ThirdPersonModule thirdPerson() {
        return thirdPerson;
    }
}
