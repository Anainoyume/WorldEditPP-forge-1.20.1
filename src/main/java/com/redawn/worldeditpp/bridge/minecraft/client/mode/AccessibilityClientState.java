package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.bridge.minecraft.network.SyncAccessibilityPayload;

public final class AccessibilityClientState {
    private static final double DEFAULT_BLOCK_REACH_DISTANCE = 4.5;
    private static final double DEFAULT_FLIGHT_SPEED = 0.12;
    public static final double MIN_GAMMA = 0.0;
    public static final double DEFAULT_GAMMA = 0.5;
    public static final double MAX_GAMMA = 12.0;

    public static double gamma = DEFAULT_GAMMA;
    public static boolean blockReachEnabled;
    public static double blockReachDistance = DEFAULT_BLOCK_REACH_DISTANCE;
    public static double flightSpeed = DEFAULT_FLIGHT_SPEED;

    public static void apply(SyncAccessibilityPayload p) {
        gamma = clampGamma(p.gamma());
        blockReachEnabled = p.blockReachEnabled();
        blockReachDistance = p.blockReachDistance();
        flightSpeed = p.flightSpeed();
        ClientEditModeController.applyServerEditMode(p.editModeActive());
        EditSubModeState.applyFromSync(p.editSubMode());
    }

    public static void setGamma(double value) {
        gamma = clampGamma(value);
    }

    public static void reset() {
        gamma = DEFAULT_GAMMA;
        blockReachEnabled = false;
        blockReachDistance = DEFAULT_BLOCK_REACH_DISTANCE;
        flightSpeed = DEFAULT_FLIGHT_SPEED;
    }

    public static double gammaForRendering(double vanillaGamma) {
        return ClientEditModeController.isEditing() ? gamma : vanillaGamma;
    }

    private static double clampGamma(double value) {
        if (!Double.isFinite(value)) return DEFAULT_GAMMA;
        return Math.max(MIN_GAMMA, Math.min(MAX_GAMMA, value));
    }

    private AccessibilityClientState() {}
}
