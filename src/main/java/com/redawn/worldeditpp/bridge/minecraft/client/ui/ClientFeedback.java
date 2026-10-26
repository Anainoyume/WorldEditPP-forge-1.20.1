package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Single client-facing feedback path for non-screen failures and transient notices. */
public final class ClientFeedback {
    private static long lastErrorMillis;
    private static String lastErrorKey;

    public static void actionBar(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(message, true);
    }

    public static void error(String key, Object... arguments) {
        long now = System.currentTimeMillis();
        // Rejected projection chunks can arrive in a burst; one action-bar notice is enough.
        if (key.equals(lastErrorKey) && now - lastErrorMillis < 1_000L) return;
        lastErrorKey = key;
        lastErrorMillis = now;
        WorldEditPlusPlus.LOGGER.warn("Client feedback: {}", key);
        actionBar(Component.translatable(key, arguments).withStyle(ChatFormatting.RED));
    }

    public static void notice(String key, Object... arguments) {
        actionBar(Component.translatable(key, arguments));
    }

    public static void resetThrottle() {
        lastErrorKey = null;
        lastErrorMillis = 0L;
    }

    private ClientFeedback() { }
}
