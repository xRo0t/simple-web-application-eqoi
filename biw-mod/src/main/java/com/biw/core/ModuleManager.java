package com.biw.core;

import com.biw.BIWMod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers modules and dispatches typed callbacks. Each callback is wrapped in
 * try/catch so that a single failing module never breaks the render loop or
 * affects the others (Requirement 2.2).
 *
 * Dispatch iterates pre-filtered typed lists with indexed for-loops to avoid
 * iterator allocation in the per-frame hot path (Requirement 8).
 */
public final class ModuleManager {

    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<RenderModule> renderModules = new ArrayList<>();
    private final List<WorldRenderModule> worldModules = new ArrayList<>();
    private final List<InputModule> inputModules = new ArrayList<>();

    public ModuleManager() {
        BIWMod.EVENTS.bind(this);
    }

    public void register(Module module) {
        byId.put(module.id(), module);
        if (module instanceof RenderModule) renderModules.add((RenderModule) module);
        if (module instanceof WorldRenderModule) worldModules.add((WorldRenderModule) module);
        if (module instanceof InputModule) inputModules.add((InputModule) module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module m : byId.values()) {
            if (type.isInstance(m)) return (T) m;
        }
        return null;
    }

    /** All registered modules in registration order (used by the settings UI). */
    public List<Module> all() {
        return new ArrayList<>(byId.values());
    }

    /** Apply persisted settings to every module. */
    public void loadAll(Config config) {
        for (Module m : byId.values()) {
            try {
                m.loadConfig(config.section(m.id()));
            } catch (Throwable t) {
                System.err.println("[BIW] failed to load config for " + m.id() + ": " + t);
            }
        }
    }

    /** Persist every module's settings. */
    public void saveAll(Config config) {
        for (Module m : byId.values()) {
            try {
                config.putSection(m.id(), m.saveConfig());
            } catch (Throwable t) {
                System.err.println("[BIW] failed to save config for " + m.id() + ": " + t);
            }
        }
        config.save();
    }

    public void dispatchClientTick() {
        for (Module m : byId.values()) {
            if (!m.isEnabled()) continue;
            try {
                m.onClientTick();
            } catch (Throwable t) {
                System.err.println("[BIW] " + m.id() + " tick error: " + t);
            }
        }
    }

    public void dispatchRenderHud(int w, int h, float partial) {
        for (int i = 0; i < renderModules.size(); i++) {
            RenderModule m = renderModules.get(i);
            if (!m.isEnabled()) continue;
            try {
                m.onRenderHud(w, h, partial);
            } catch (Throwable t) {
                System.err.println("[BIW] " + m.id() + " HUD render error: " + t);
            }
        }
    }

    public void dispatchRenderWorld(float partial) {
        for (int i = 0; i < worldModules.size(); i++) {
            WorldRenderModule m = worldModules.get(i);
            if (!m.isEnabled()) continue;
            try {
                m.onRenderWorld(partial);
            } catch (Throwable t) {
                System.err.println("[BIW] " + m.id() + " world render error: " + t);
            }
        }
    }

    public void dispatchKey(int keyCode, boolean pressed) {
        for (int i = 0; i < inputModules.size(); i++) {
            InputModule m = inputModules.get(i);
            if (!m.isEnabled()) continue;
            try {
                m.onKey(keyCode, pressed);
            } catch (Throwable t) {
                System.err.println("[BIW] " + m.id() + " key error: " + t);
            }
        }
    }
}
