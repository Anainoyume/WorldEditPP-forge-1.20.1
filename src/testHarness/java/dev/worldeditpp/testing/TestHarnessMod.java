package dev.worldeditpp.testing;

import java.io.File;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraftforge.fml.common.Mod;

@Mod(TestHarnessMod.ID)
public final class TestHarnessMod {
    public static final String ID = "worldeditpp_test";

    public TestHarnessMod() throws Exception {
        if (Boolean.getBoolean("forge.gameTestServer")) {
            GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(System.getProperty("worldeditpp.test.report"))));
        }
    }
}
