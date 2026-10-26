package dev.worldeditpp.testing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void modAndServerLifecycle(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(System.getProperty("worldeditpp.test.expectedMod")), "Target mod must load");
        helper.assertTrue(helper.getLevel().getServer().isSameThread(), "World assertions must use the server thread");
        helper.assertFalse(Boolean.getBoolean("worldeditpp.test.fail"), "Intentional failure probe");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 1));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void offlinePlayerIdentity(GameTestHelper helper) {
        String name = "WEPPTestPlayer";
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, name));
        helper.assertTrue(player.getUUID().equals(id), "Offline profile UUID");
        helper.assertTrue(player.getUUID().version() == 3, "Offline UUID version");
        helper.assertTrue(player.serverLevel() == helper.getLevel(), "Fake player belongs to the test world");
        helper.succeed();
    }
}
