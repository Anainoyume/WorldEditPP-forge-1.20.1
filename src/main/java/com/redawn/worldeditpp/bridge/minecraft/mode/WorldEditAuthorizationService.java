package com.redawn.worldeditpp.bridge.minecraft.mode;

import com.redawn.worldeditpp.mode.WorldEditAuthorizationRules;

import com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Environment;
import com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Result;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative access check for every WorldEdit++ operation. */
public final class WorldEditAuthorizationService {
    private static final int REQUIRED_COMMAND_PERMISSION_LEVEL = 2;
    private static final long DENIAL_MESSAGE_COOLDOWN_MS = 1_000L;
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE = new HashMap<>();

    public static boolean isAuthorized(ServerPlayer player) {
        return result(player) == Result.ALLOWED;
    }

    public static boolean require(ServerPlayer player) {
        Result result = result(player);
        if (result == Result.ALLOWED) return true;
        deny(player, result);
        return false;
    }

    public static void deny(ServerPlayer player) {
        Result result = result(player);
        if (result != Result.ALLOWED) deny(player, result);
    }

    public static void clear(ServerPlayer player) {
        LAST_DENIAL_MESSAGE.remove(player.getUUID());
    }

    private static Result result(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        Environment environment;
        boolean cheatsEnabled;
        if (server.isSingleplayer()) {
            if (server.isPublished()) {
                environment = Environment.LAN;
                cheatsEnabled = server.getPlayerList().isAllowCheatsForAllPlayers();
            } else {
                environment = Environment.SINGLEPLAYER;
                cheatsEnabled = server.getWorldData().getAllowCommands();
            }
        } else {
            environment = Environment.DEDICATED_SERVER;
            cheatsEnabled = true;
        }

        return WorldEditAuthorizationRules.evaluate(
                environment,
                cheatsEnabled,
                server.isSingleplayerOwner(player.getGameProfile()),
                player.hasPermissions(REQUIRED_COMMAND_PERMISSION_LEVEL));
    }

    private static void deny(ServerPlayer player, Result result) {
        long now = System.currentTimeMillis();
        long previous = LAST_DENIAL_MESSAGE.getOrDefault(player.getUUID(), 0L);
        if (now - previous < DENIAL_MESSAGE_COOLDOWN_MS) return;
        LAST_DENIAL_MESSAGE.put(player.getUUID(), now);

        String key = switch (result) {
            case SINGLEPLAYER_CHEATS_REQUIRED -> "message.worldeditpp.permission.singleplayer_cheats_required";
            case LAN_CHEATS_REQUIRED -> "message.worldeditpp.permission.lan_cheats_required";
            case OPERATOR_PERMISSION_REQUIRED -> "message.worldeditpp.permission.operator_required";
            case ALLOWED -> throw new IllegalArgumentException("Cannot deny an authorized player");
        };
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
    }

    private WorldEditAuthorizationService() { }
}
