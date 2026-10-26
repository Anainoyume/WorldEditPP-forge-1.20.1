package dev.worldeditpp.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Furnished multi-floor building; observes item creation, never deletes drops to make a test pass. */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID)
public final class BuildingStressFixture {
    private static volatile boolean armed;
    private static final AtomicInteger drops = new AtomicInteger();
    private static volatile String firstDrop;

    static BlockState state(int x, int y, int z) {
        int rx = x % 12, ry = y % 16, rz = z % 16;
        if (ry == 0) return Blocks.OAK_PLANKS.defaultBlockState();
        if (rx == 0 && rz == 0) return Blocks.STONE_BRICKS.defaultBlockState();
        if ((x == 0 || x == 119 || z == 0 || z == 63) && ry < 8)
            return (ry >= 3 && ry <= 5 ? Blocks.GLASS : Blocks.BRICKS).defaultBlockState();
        if (ry == 1 && rz == 3) return switch (rx) {
            case 2 -> Blocks.CHEST.defaultBlockState();
            case 3 -> Blocks.BARREL.defaultBlockState();
            case 4 -> Blocks.HOPPER.defaultBlockState();
            case 5 -> Blocks.FURNACE.defaultBlockState();
            case 6 -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
            case 7 -> Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true);
            default -> Blocks.AIR.defaultBlockState();
        };
        if (ry == 1 && rz == 6 && rx == 3) return Blocks.TORCH.defaultBlockState();
        if (ry == 1 && rz == 8 && rx >= 2 && rx <= 8) return Blocks.OAK_STAIRS.defaultBlockState();
        if (ry == 1 && rz == 10 && rx >= 2 && rx <= 8) return Blocks.OAK_SLAB.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }

    static void populate(ServerLevel level, BlockPos origin, int sx, int sy, int sz) {
        visit(origin, sx, sy, sz, pos -> {
            var entity = level.getBlockEntity(pos);
            ItemStack stack = new ItemStack(level.getBlockState(pos).is(Blocks.JUKEBOX) ? Items.MUSIC_DISC_13 : Items.DIAMOND,
                    level.getBlockState(pos).is(Blocks.JUKEBOX) ? 1 : 7);
            stack.getOrCreateTag().putString("wepp_building_fixture", "inventory-must-survive");
            if (entity instanceof CampfireBlockEntity fire) fire.getItems().set(0, stack);
            else if (entity instanceof Container container) container.setItem(0, stack);
            else throw new AssertionError("Missing fixture block entity at " + pos);
            entity.setChanged();
        });
    }

    static void verifyContents(ServerLevel level, BlockPos origin, int sx, int sy, int sz) {
        visit(origin, sx, sy, sz, pos -> {
            var entity = level.getBlockEntity(pos);
            ItemStack stack = entity instanceof CampfireBlockEntity fire ? fire.getItems().get(0)
                    : entity instanceof Container container ? container.getItem(0) : ItemStack.EMPTY;
            int expectedCount = level.getBlockState(pos).is(Blocks.JUKEBOX) ? 1 : 7;
            var expectedItem = expectedCount == 1 ? Items.MUSIC_DISC_13 : Items.DIAMOND;
            if (!stack.is(expectedItem) || stack.getCount() != expectedCount || stack.getTag() == null
                    || !"inventory-must-survive".equals(stack.getTag().getString("wepp_building_fixture")))
                throw new AssertionError("Container NBT mismatch at " + pos + ": " + stack);
        });
    }

    // These containers occupy air cells in the incoming building. Both undo and redo must
    // remove loaded inventories, and redo must leave the original snapshot reusable.
    static BlockState originalTargetState(int x, int y, int z) {
        return x % 12 == 9 && y % 16 == 1 && z % 16 == 3
                ? Blocks.CHEST.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    static void populateOriginalTarget(ServerLevel level, BlockPos origin, int sx, int sy, int sz) {
        visitOriginal(origin, sx, sy, sz, pos -> {
            level.getChunkAt(pos).setBlockState(pos, Blocks.CHEST.defaultBlockState(), false);
            ItemStack stack = new ItemStack(Items.EMERALD, 11);
            stack.getOrCreateTag().putString("wepp_building_fixture", "original-target");
            ((Container) level.getBlockEntity(pos)).setItem(0, stack);
            level.getBlockEntity(pos).setChanged();
        });
    }

    static void verifyOriginalTarget(ServerLevel level, BlockPos origin, int sx, int sy, int sz) {
        visitOriginal(origin, sx, sy, sz, pos -> {
            if (!(level.getBlockEntity(pos) instanceof Container container))
                throw new AssertionError("Original target container missing at " + pos);
            ItemStack stack = container.getItem(0);
            if (!stack.is(Items.EMERALD) || stack.getCount() != 11 || stack.getTag() == null
                    || !"original-target".equals(stack.getTag().getString("wepp_building_fixture")))
                throw new AssertionError("Original target NBT mismatch at " + pos + ": " + stack);
        });
    }

    private static void visitOriginal(BlockPos origin, int sx, int sy, int sz, java.util.function.Consumer<BlockPos> action) {
        for (int y = 1; y < sy; y += 16) for (int z = 3; z < sz; z += 16)
            for (int x = 9; x < sx; x += 12) action.accept(origin.offset(x, y, z));
    }

    private static void visit(BlockPos origin, int sx, int sy, int sz, java.util.function.Consumer<BlockPos> action) {
        for (int y = 1; y < sy; y += 16) for (int z = 3; z < sz; z += 16)
            for (int x = 0; x < sx; x += 12) for (int dx = 2; dx <= 7; dx++)
                action.accept(origin.offset(x + dx, y, z));
    }

    static void arm() { drops.set(0); firstDrop = null; armed = true; }

    static void calibrate(ServerLevel level) {
        arm();
        // Positive control before the measured operation. Remove only this exact sentinel,
        // never a queried group of entities; subsequent operation drops remain untouched.
        var sentinel = new ItemEntity(level, 0.5, 250, 0.5, new ItemStack(Items.STICK));
        try {
            if (!level.addFreshEntity(sentinel) || drops.get() != 1)
                throw new AssertionError("ItemEntity observer did not detect its calibration sentinel");
        } finally { sentinel.discard(); }
        arm();
        verifyNoDrops();
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!armed || event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) return;
        if (drops.incrementAndGet() == 1) {
            firstDrop = item.getItem() + " at " + item.position() + "\n"
                    + java.util.Arrays.toString(Thread.currentThread().getStackTrace());
            try { Files.writeString(report(), firstDrop); }
            catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
        }
    }

    static void verifyNoDrops() {
        if (drops.get() != 0) throw new AssertionError("Building operation spawned " + drops.get() + " item entities: " + firstDrop);
        try { Files.writeString(report(), "ItemEntity spawn events since fixture setup: 0\n"); }
        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static Path report() {
        return Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling(
                Boolean.getBoolean("worldeditpp.test.chestStress") ? "chest-drops.txt"
                        : Boolean.getBoolean("worldeditpp.test.containerStress") ? "container-drops.txt"
                        : "building-drops.txt");
    }
}
