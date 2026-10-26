package com.redawn.worldeditpp.mode;

import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Environment.DEDICATED_SERVER;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Environment.LAN;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Environment.SINGLEPLAYER;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Result.ALLOWED;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Result.LAN_CHEATS_REQUIRED;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Result.OPERATOR_PERMISSION_REQUIRED;
import static com.redawn.worldeditpp.mode.WorldEditAuthorizationRules.Result.SINGLEPLAYER_CHEATS_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldEditAuthorizationRulesTest {
    @Test
    void singleplayerRequiresCheatsAndTheOwnerPermission() {
        assertEquals(SINGLEPLAYER_CHEATS_REQUIRED,
                WorldEditAuthorizationRules.evaluate(SINGLEPLAYER, false, true, false));
        assertEquals(OPERATOR_PERMISSION_REQUIRED,
                WorldEditAuthorizationRules.evaluate(SINGLEPLAYER, true, false, true));
        assertEquals(OPERATOR_PERMISSION_REQUIRED,
                WorldEditAuthorizationRules.evaluate(SINGLEPLAYER, true, true, false));
        assertEquals(ALLOWED,
                WorldEditAuthorizationRules.evaluate(SINGLEPLAYER, true, true, true));
    }

    @Test
    void lanRequiresThePublishedCheatFlagAndPlayerPermission() {
        assertEquals(LAN_CHEATS_REQUIRED,
                WorldEditAuthorizationRules.evaluate(LAN, false, true, true));
        assertEquals(LAN_CHEATS_REQUIRED,
                WorldEditAuthorizationRules.evaluate(LAN, false, false, true));
        assertEquals(OPERATOR_PERMISSION_REQUIRED,
                WorldEditAuthorizationRules.evaluate(LAN, true, false, false));
        assertEquals(ALLOWED,
                WorldEditAuthorizationRules.evaluate(LAN, true, false, true));
    }

    @Test
    void dedicatedServerRequiresOperatorPermission() {
        assertEquals(OPERATOR_PERMISSION_REQUIRED,
                WorldEditAuthorizationRules.evaluate(DEDICATED_SERVER, true, false, false));
        assertEquals(ALLOWED,
                WorldEditAuthorizationRules.evaluate(DEDICATED_SERVER, true, false, true));
    }
}
