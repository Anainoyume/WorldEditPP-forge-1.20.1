package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.sk89q.worldedit.WorldEdit;
import net.minecraft.world.item.ItemStack;

@GameTestSuiteDescriptor(name = "WorldEdit tool configuration")
public final class WorldEditToolConfigurationTestSuite extends TestSuiteBase {
    @Override
    protected void runTests(GameSimulation simulation) {
        var configuration = WorldEdit.getInstance().getConfiguration();
        test("custom wand is the default selection tool", () -> GameAssertions.assertEquals(
                "worldeditpp:worldeditpp_wand", configuration.wandItem,
                "WorldEdit++ must own the default selection wand while installed"));
        test("default compass navigation tool is unbound", () -> GameAssertions.assertEquals(
                "worldeditpp:disabled_navigation_wand", configuration.navigationWand,
                "The WorldEdit compass navigation wand must not be assigned"));
        test("legacy navigation wand actions are disabled", () -> GameAssertions.assertEquals(
                0, configuration.navigationWandMaxDistance,
                "WorldEdit navigation-wand actions must remain disabled"));
        test("animated wand name keeps vanilla highlight timer stable", () -> {
            ItemStack wand = new ItemStack(ModItems.WORLD_EDIT_WAND.get());
            GameAssertions.check(wand.getHoverName() == wand.getHoverName(),
                    "Animated colors must not replace the name component and restart the selected-item timer");
            GameAssertions.assertEquals("WorldEdit Wand", wand.getHoverName().getString(),
                    "The wand name must resolve from its registered item ID instead of minecraft:air");
        });
    }
}
