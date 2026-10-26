package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

@GameTestSuiteDescriptor(name = "Projection block entity snapshots")
public final class ProjectionBlockEntitySnapshotTestSuite extends TestSuiteBase {
    private static final String PERSISTENT_KEY = "worldeditpp_snapshot_test";

    @Override
    protected void runTests(GameSimulation simulation) {
        test("chest NBT is copied independently", () -> testChestSnapshot(simulation));
        test("sign text NBT is copied independently", () -> testSignSnapshot(simulation));
        test("banner pattern NBT is copied independently", () -> testBannerSnapshot(simulation));
    }

    private void testChestSnapshot(GameSimulation simulation) {
        BlockPos sourcePos = new BlockPos(12, 70, -8);
        BlockPos restoredPos = new BlockPos(1200, 90, -800);
        BlockState state = Blocks.CHEST.defaultBlockState();
        ChestBlockEntity source = new ChestBlockEntity(sourcePos, state);
        source.setItem(4, new ItemStack(Items.DIAMOND, 23));
        source.getPersistentData().putString(PERSISTENT_KEY, "captured");

        SelectionBlockEntityData snapshot = ProjectionBlockEntityNbt.capture(
                7, source, simulation.server().registryAccess());

        source.setItem(4, new ItemStack(Items.DIRT, 1));
        source.getPersistentData().putString(PERSISTENT_KEY, "mutated");

        BlockEntity restored = ProjectionBlockEntityNbt.restore(
                snapshot, restoredPos, state, simulation.server().registryAccess());
        GameAssertions.check(restored instanceof ChestBlockEntity,
                "Snapshot must restore a chest block entity");
        GameAssertions.check(restored != source,
                "Snapshot must restore a distinct block entity instance");
        GameAssertions.assertEquals(restoredPos, restored.getBlockPos(),
                "Restored copy must use its snapshot render position");
        GameAssertions.check(!restored.hasLevel(),
                "Restored copy must stay detached from the source world");

        ChestBlockEntity restoredChest = (ChestBlockEntity) restored;
        GameAssertions.check(restoredChest.getItem(4).is(Items.DIAMOND),
                "Restored chest must preserve the captured item type");
        GameAssertions.check(restoredChest.getItem(4).getCount() == 23,
                "Restored chest must preserve the captured item count");
        GameAssertions.assertEquals("captured", restoredChest.getPersistentData().getString(PERSISTENT_KEY),
                "Restored chest must preserve custom persistent NBT");
    }

    private void testSignSnapshot(GameSimulation simulation) {
        BlockPos pos = new BlockPos(20, 75, 20);
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        SignBlockEntity source = new SignBlockEntity(pos, state);
        source.setLevel(simulation.overworld());
        SignText capturedText = new SignText()
                .setMessage(0, Component.literal("snapshot text"))
                .setColor(DyeColor.BLUE)
                .setHasGlowingText(true);
        source.setText(capturedText, true);
        source.setWaxed(true);

        SelectionBlockEntityData snapshot = ProjectionBlockEntityNbt.capture(
                8, source, simulation.server().registryAccess());

        source.setText(new SignText().setMessage(0, Component.literal("mutated")), true);
        source.setWaxed(false);

        BlockEntity restored = ProjectionBlockEntityNbt.restore(
                snapshot, pos, state, simulation.server().registryAccess());
        GameAssertions.check(restored instanceof SignBlockEntity,
                "Snapshot must restore a sign block entity");
        SignBlockEntity restoredSign = (SignBlockEntity) restored;
        GameAssertions.assertEquals("snapshot text", restoredSign.getFrontText().getMessage(0, false).getString(),
                "Restored sign must preserve captured text");
        GameAssertions.assertEquals(DyeColor.BLUE, restoredSign.getFrontText().getColor(),
                "Restored sign must preserve text color");
        GameAssertions.check(restoredSign.getFrontText().hasGlowingText(),
                "Restored sign must preserve glowing text");
        GameAssertions.check(restoredSign.isWaxed(),
                "Restored sign must preserve wax state");
    }

    private void testBannerSnapshot(GameSimulation simulation) {
        BlockPos pos = new BlockPos(-20, 75, 20);
        BlockState state = Blocks.RED_BANNER.defaultBlockState();
        var patterns = new BannerPattern.Builder().addPattern(BannerPatterns.CREEPER, DyeColor.BLACK).toListTag();
        ItemStack bannerItem = new ItemStack(Items.RED_BANNER);
        bannerItem.getOrCreateTagElement("BlockEntityTag").put("Patterns", patterns);
        bannerItem.setHoverName(Component.literal("Snapshot Banner"));
        BannerBlockEntity source = new BannerBlockEntity(pos, state);
        source.fromItem(bannerItem, DyeColor.RED);

        var sourcePatterns = java.util.List.copyOf(source.getPatterns());
        SelectionBlockEntityData snapshot = ProjectionBlockEntityNbt.capture(
                9, source, simulation.server().registryAccess());

        source.fromItem(new ItemStack(Items.WHITE_BANNER), DyeColor.WHITE);

        BlockEntity restored = ProjectionBlockEntityNbt.restore(
                snapshot, pos, state, simulation.server().registryAccess());
        GameAssertions.check(restored instanceof BannerBlockEntity,
                "Snapshot must restore a banner block entity");
        BannerBlockEntity restoredBanner = (BannerBlockEntity) restored;
        GameAssertions.assertEquals(DyeColor.RED, restoredBanner.getBaseColor(),
                "Restored banner must preserve its block-state color");
        GameAssertions.assertEquals(sourcePatterns, restoredBanner.getPatterns(),
                "Restored banner must preserve pattern layers");
        GameAssertions.assertEquals("Snapshot Banner", restoredBanner.getCustomName().getString(),
                "Restored banner must preserve its custom name");
    }
}
