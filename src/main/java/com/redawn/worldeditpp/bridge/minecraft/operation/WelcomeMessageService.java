package com.redawn.worldeditpp.bridge.minecraft.operation;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.network.WelcomeMessagePayload;
import net.minecraft.server.level.ServerPlayer;

/** Save-owned welcome preference; the client owns localized text and bound key names. */
public final class WelcomeMessageService {
    private static final String DISMISSED = "worldeditppWelcomeDismissed";

    public static void login(ServerPlayer player) {
        if (!PlatformBridge.persistentData(player).getBoolean(DISMISSED)) {
            PlatformBridge.sendToPlayer(player, new WelcomeMessagePayload());
        }
    }

    public static void dismiss(ServerPlayer player) {
        PlatformBridge.persistentData(player).putBoolean(DISMISSED, true);
    }

    private WelcomeMessageService() { }
}
