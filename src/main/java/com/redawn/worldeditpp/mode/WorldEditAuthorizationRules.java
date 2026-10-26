package com.redawn.worldeditpp.mode;

/** Pure authorization rules shared by the server adapter and unit tests. */
public final class WorldEditAuthorizationRules {
    public enum Environment {
        SINGLEPLAYER,
        LAN,
        DEDICATED_SERVER
    }

    public enum Result {
        ALLOWED,
        SINGLEPLAYER_CHEATS_REQUIRED,
        LAN_CHEATS_REQUIRED,
        OPERATOR_PERMISSION_REQUIRED
    }

    public static Result evaluate(
            Environment environment,
            boolean cheatsEnabled,
            boolean owner,
            boolean hasCommandPermission
    ) {
        return switch (environment) {
            case SINGLEPLAYER -> {
                if (!cheatsEnabled) yield Result.SINGLEPLAYER_CHEATS_REQUIRED;
                yield owner && hasCommandPermission
                        ? Result.ALLOWED
                        : Result.OPERATOR_PERMISSION_REQUIRED;
            }
            case LAN -> {
                if (!cheatsEnabled) yield Result.LAN_CHEATS_REQUIRED;
                yield hasCommandPermission
                        ? Result.ALLOWED
                        : Result.OPERATOR_PERMISSION_REQUIRED;
            }
            case DEDICATED_SERVER -> hasCommandPermission
                    ? Result.ALLOWED
                    : Result.OPERATOR_PERMISSION_REQUIRED;
        };
    }

    private WorldEditAuthorizationRules() { }
}
