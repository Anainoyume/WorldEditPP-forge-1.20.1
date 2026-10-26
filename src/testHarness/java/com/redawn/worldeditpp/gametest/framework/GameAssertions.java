package com.redawn.worldeditpp.gametest.framework;

import com.redawn.common.test.TestAssertions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public final class GameAssertions {

    public static void check(boolean condition, String message) {
        TestAssertions.check(condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        TestAssertions.equal(expected, actual, message);
    }

    public static void assertEqualsFloat(float expected, float actual, float delta, String message) {
        TestAssertions.equalFloat(expected, actual, delta, message);
    }

    public static void assertPlayerCreative(ServerPlayer player) {
        check(player.isCreative(), player.getName().getString() + " should be in creative mode");
    }

    public static void assertPlayerNoPhysics(Player player) {
        check(player.noPhysics, player.getName().getString() + " should have noPhysics enabled");
        check(!player.blocksBuilding, player.getName().getString() + " should have blocksBuilding disabled");
    }

    public static void assertPlayerFlying(Player player) {
        Abilities abilities = player.getAbilities();
        check(abilities.mayfly, player.getName().getString() + " should be able to fly");
        check(abilities.flying, player.getName().getString() + " should be flying");
    }

    public static void assertPlayerInvulnerable(Player player) {
        check(player.getAbilities().invulnerable, player.getName().getString() + " should be invulnerable");
    }

    public static void assertPlayerNoGravity(Player player) {
        check(player.isNoGravity(), player.getName().getString() + " should have no gravity");
    }

    public static void assertPlayerFlyingSpeed(Player player, float expected, float delta) {
        assertEqualsFloat(expected, player.getAbilities().getFlyingSpeed(), delta,
                player.getName().getString() + " flying speed mismatch");
    }

    public static void assertPlayerGameMode(ServerPlayer player, GameType expected) {
        assertEquals(expected, player.gameMode.getGameModeForPlayer(),
                player.getName().getString() + " game mode mismatch");
    }

    private GameAssertions() {}
}
