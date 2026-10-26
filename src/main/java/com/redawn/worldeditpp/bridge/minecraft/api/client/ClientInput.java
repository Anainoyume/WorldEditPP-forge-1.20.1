package com.redawn.worldeditpp.bridge.minecraft.api.client;

import net.minecraft.client.Camera;

/** Per-dispatch input values. Only the loader translates consumption into event cancellation. */
public final class ClientInput {
    public static class Consumption {
        private boolean consumed;
        public final void setCanceled(boolean value) { consumed = value; }
        public final boolean consumed() { return consumed; }
    }
    public static final class Button extends Consumption {
        private final int button, action;
        public Button(int button, int action) { this.button = button; this.action = action; }
        public int getButton() { return button; }
        public int getAction() { return action; }
    }
    public static final class Scroll extends Consumption {
        private final double delta;
        public Scroll(double delta) { this.delta = delta; }
        public double getScrollDeltaY() { return delta; }
    }
    public static final class Highlight extends Consumption {
        private final Camera camera;
        public Highlight(Camera camera) { this.camera = camera; }
        public Camera getCamera() { return camera; }
    }
    private ClientInput() { }
}
