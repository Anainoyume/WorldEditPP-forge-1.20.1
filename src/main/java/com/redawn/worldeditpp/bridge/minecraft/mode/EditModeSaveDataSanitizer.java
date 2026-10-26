package com.redawn.worldeditpp.bridge.minecraft.mode;

import net.minecraft.nbt.CompoundTag;

/** Replaces temporary edit-mode values before player data is written to disk. */
public final class EditModeSaveDataSanitizer {
    private static final String ABILITIES = "abilities";

    public static void sanitize(CompoundTag saveData, Snapshot original) {
        CompoundTag abilities = saveData.getCompound(ABILITIES);
        abilities.putBoolean("invulnerable", original.invulnerable());
        abilities.putBoolean("flying", original.flying());
        abilities.putBoolean("mayfly", original.mayfly());
        abilities.putBoolean("instabuild", original.instabuild());
        abilities.putBoolean("mayBuild", original.mayBuild());
        abilities.putFloat("flySpeed", original.flyingSpeed());
        abilities.putFloat("walkSpeed", original.walkingSpeed());
        saveData.put(ABILITIES, abilities);

        saveData.putInt("playerGameType", original.gameTypeId());
        if (original.previousGameTypeId() == null) {
            saveData.remove("previousPlayerGameType");
        } else {
            saveData.putInt("previousPlayerGameType", original.previousGameTypeId());
        }

        if (original.noGravity()) {
            saveData.putBoolean("NoGravity", true);
        } else {
            saveData.remove("NoGravity");
        }
    }

    public record Snapshot(
            int gameTypeId,
            Integer previousGameTypeId,
            boolean invulnerable,
            boolean flying,
            boolean mayfly,
            boolean instabuild,
            boolean mayBuild,
            float flyingSpeed,
            float walkingSpeed,
            boolean noGravity
    ) { }

    private EditModeSaveDataSanitizer() { }
}
