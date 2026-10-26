package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeSaveDataSanitizer;
import net.minecraft.nbt.CompoundTag;

@GameTestSuiteDescriptor(name = "Edit mode persistence")
public final class EditModePersistenceTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        test("temporary movement values are removed from player saves", this::testOriginalValuesReplaceEditValues);
        test("optional original values retain vanilla NBT semantics", this::testOptionalValues);
    }

    private void testOriginalValuesReplaceEditValues() {
        CompoundTag saveData = pollutedEditModeData();
        EditModeSaveDataSanitizer.sanitize(saveData, new EditModeSaveDataSanitizer.Snapshot(
                1, 0, false, true, true, true, false, 0.05F, 0.1F, false));

        CompoundTag abilities = saveData.getCompound("abilities");
        GameAssertions.check(!abilities.getBoolean("invulnerable"), "Invulnerability must use the original value");
        GameAssertions.check(abilities.getBoolean("flying"), "Flying must use the original value");
        GameAssertions.check(abilities.getBoolean("mayfly"), "Mayfly must use the original value");
        GameAssertions.check(abilities.getBoolean("instabuild"), "Instabuild must use the original value");
        GameAssertions.check(!abilities.getBoolean("mayBuild"), "MayBuild must use the original value");
        GameAssertions.assertEqualsFloat(0.05F, abilities.getFloat("flySpeed"), 0.0001F,
                "Flight speed must use the pre-edit creative speed");
        GameAssertions.assertEqualsFloat(0.1F, abilities.getFloat("walkSpeed"), 0.0001F,
                "Walking speed must use the original value");
        GameAssertions.assertEquals(1, saveData.getInt("playerGameType"), "Game mode must use the original value");
        GameAssertions.assertEquals(0, saveData.getInt("previousPlayerGameType"),
                "Previous game mode must use the original value");
        GameAssertions.check(!saveData.contains("NoGravity"), "Temporary no-gravity must not be saved");
        GameAssertions.check(abilities.getBoolean("customAbility"), "Unrelated ability data must be retained");
    }

    private void testOptionalValues() {
        CompoundTag saveData = pollutedEditModeData();
        EditModeSaveDataSanitizer.sanitize(saveData, new EditModeSaveDataSanitizer.Snapshot(
                3, null, true, false, true, false, true, 0.2F, 0.15F, true));

        GameAssertions.check(saveData.getBoolean("NoGravity"), "Original no-gravity must be retained");
        GameAssertions.check(!saveData.contains("previousPlayerGameType"),
                "A missing original previous game mode must remain absent");
    }

    private static CompoundTag pollutedEditModeData() {
        CompoundTag saveData = new CompoundTag();
        CompoundTag abilities = new CompoundTag();
        abilities.putBoolean("invulnerable", true);
        abilities.putBoolean("flying", true);
        abilities.putBoolean("mayfly", true);
        abilities.putBoolean("instabuild", true);
        abilities.putBoolean("mayBuild", true);
        abilities.putFloat("flySpeed", 0.6F);
        abilities.putFloat("walkSpeed", 0.1F);
        abilities.putBoolean("customAbility", true);
        saveData.put("abilities", abilities);
        saveData.putInt("playerGameType", 1);
        saveData.putInt("previousPlayerGameType", 2);
        saveData.putBoolean("NoGravity", true);
        return saveData;
    }
}
