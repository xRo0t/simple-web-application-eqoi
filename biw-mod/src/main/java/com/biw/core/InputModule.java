package com.biw.core;

/**
 * A module that reacts to a key state. Implementations must keep work small
 * because this is called from the client tick.
 */
public abstract class InputModule extends Module {

    protected InputModule(String id, boolean enabledByDefault) {
        super(id, enabledByDefault);
    }

    /**
     * @param keyCode LWJGL key code
     * @param pressed true when pressed/held, false when released
     */
    public abstract void onKey(int keyCode, boolean pressed);
}
