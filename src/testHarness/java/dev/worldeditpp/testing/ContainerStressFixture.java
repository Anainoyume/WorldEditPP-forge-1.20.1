package dev.worldeditpp.testing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * Every 1.20.1 block-entity inventory that can hold items, on one test pad.
 *
 * <p>Manual inventories use marked stacks so a copied or undone container must return the exact
 * item, count and NBT per slot. Loot-table containers are populated through the same
 * {@code unpackLootTable} path worldgen uses; a second set stays unopened to prove that copying,
 * pasting and undo never force a lazy loot roll.</p>
 */
final class ContainerStressFixture {
    static final int SIZE_X = 16;
    static final int SIZE_Y = 2;
    static final int SIZE_Z = 16;

    /** High above the flat-world floor: if contents ever drop, they fall with no floor below. */
    static final BlockPos VOID_CHEST = new BlockPos(64, 180, 0);
    static final BlockPos VOID_SUPPORT = new BlockPos(64, 179, 0);

    record Loot(String table, long seed, boolean opened) {}

    enum Filler { ALL, FURNACE, BREWING, BOOKS, JUKEBOX, CAMPFIRE, LECTERN }

    record Case(String name, int x, int z, BlockState state, Filler filler, Loot loot) {}

    /** Absolute cell captured from the live server world; compared cell by cell after every edit. */
    record Cell(BlockPos pos, BlockState state, List<ItemStack> items, String lootTable) {}

    private static final List<Case> CASES = buildCases();

    private static List<Case> buildCases() {
        List<Case> cases = new ArrayList<>();
        cases.add(manual("chest", 0, 0, Blocks.CHEST, Filler.ALL));
        cases.add(manual("trapped_chest", 1, 0, Blocks.TRAPPED_CHEST, Filler.ALL));
        cases.add(manual("barrel", 2, 0, Blocks.BARREL, Filler.ALL));
        cases.add(new Case("double_chest_left", 3, 0,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT), Filler.ALL, null));
        cases.add(new Case("double_chest_right", 4, 0,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT), Filler.ALL, null));
        cases.add(loot("chest_loot_opened", 5, 0, Blocks.CHEST, "minecraft:chests/simple_dungeon", 11L, true));
        cases.add(loot("chest_loot_unopened", 6, 0, Blocks.CHEST, "minecraft:chests/abandoned_mineshaft", 21L, false));
        cases.add(loot("barrel_loot_opened", 7, 0, Blocks.BARREL, "minecraft:chests/village/village_plains_house", 31L, true));
        cases.add(loot("barrel_loot_unopened", 8, 0, Blocks.BARREL, "minecraft:chests/nether_bridge", 41L, false));
        cases.add(loot("shulker_loot_opened", 9, 0, Blocks.SHULKER_BOX, "minecraft:chests/stronghold_library", 51L, true));
        cases.add(loot("shulker_loot_unopened", 10, 0, Blocks.SHULKER_BOX, "minecraft:chests/shipwreck_treasure", 61L, false));
        cases.add(manual("hopper", 11, 0, Blocks.HOPPER, Filler.ALL));
        // A ticking hopper unpacks its own loot table through getItem; keep the lazy case on a
        // container that only unpacks when opened (trapped chest).
        cases.add(loot("trapped_chest_loot_unopened", 12, 0, Blocks.TRAPPED_CHEST,
                "minecraft:chests/woodland_mansion", 71L, false));
        cases.add(manual("dropper", 13, 0, Blocks.DROPPER, Filler.ALL));
        cases.add(loot("dropper_loot_opened", 14, 0, Blocks.DROPPER, "minecraft:chests/pillager_outpost", 81L, true));
        cases.add(manual("dispenser", 15, 0, Blocks.DISPENSER, Filler.ALL));

        cases.add(loot("dispenser_loot_unopened", 0, 1, Blocks.DISPENSER, "minecraft:chests/buried_treasure", 91L, false));
        cases.add(manual("furnace", 1, 1, Blocks.FURNACE, Filler.FURNACE));
        cases.add(manual("blast_furnace", 2, 1, Blocks.BLAST_FURNACE, Filler.FURNACE));
        cases.add(manual("smoker", 3, 1, Blocks.SMOKER, Filler.FURNACE));
        cases.add(manual("brewing_stand", 4, 1, Blocks.BREWING_STAND, Filler.BREWING));
        cases.add(manual("chiseled_bookshelf", 5, 1, Blocks.CHISELED_BOOKSHELF, Filler.BOOKS));
        cases.add(new Case("jukebox", 6, 1,
                Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true), Filler.JUKEBOX, null));
        cases.add(new Case("campfire", 7, 1,
                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Filler.CAMPFIRE, null));
        cases.add(new Case("soul_campfire", 8, 1,
                Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Filler.CAMPFIRE, null));
        cases.add(new Case("lectern", 9, 1,
                Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true), Filler.LECTERN, null));

        BlockState[] shulkers = {
                Blocks.WHITE_SHULKER_BOX.defaultBlockState(), Blocks.ORANGE_SHULKER_BOX.defaultBlockState(),
                Blocks.MAGENTA_SHULKER_BOX.defaultBlockState(), Blocks.LIGHT_BLUE_SHULKER_BOX.defaultBlockState(),
                Blocks.YELLOW_SHULKER_BOX.defaultBlockState(), Blocks.LIME_SHULKER_BOX.defaultBlockState(),
                Blocks.PINK_SHULKER_BOX.defaultBlockState(), Blocks.GRAY_SHULKER_BOX.defaultBlockState(),
                Blocks.LIGHT_GRAY_SHULKER_BOX.defaultBlockState(), Blocks.CYAN_SHULKER_BOX.defaultBlockState(),
                Blocks.PURPLE_SHULKER_BOX.defaultBlockState(), Blocks.BLUE_SHULKER_BOX.defaultBlockState(),
                Blocks.BROWN_SHULKER_BOX.defaultBlockState(), Blocks.GREEN_SHULKER_BOX.defaultBlockState(),
                Blocks.RED_SHULKER_BOX.defaultBlockState(), Blocks.BLACK_SHULKER_BOX.defaultBlockState()
        };
        for (int color = 0; color < shulkers.length; color++) {
            cases.add(new Case("shulker_color_" + color, color, 2, shulkers[color], Filler.ALL, null));
        }
        return List.copyOf(cases);
    }

    private static Case manual(String name, int x, int z, net.minecraft.world.level.block.Block block, Filler filler) {
        return new Case(name, x, z, block.defaultBlockState(), filler, null);
    }

    private static Case loot(String name, int x, int z, net.minecraft.world.level.block.Block block,
                             String table, long seed, boolean opened) {
        return new Case(name, x, z, block.defaultBlockState(), null, new Loot(table, seed, opened));
    }

    static int containerCount() {
        return CASES.size();
    }

    /** Fills the pad and the independent void chest; runs on the server thread. */
    static void populate(ServerLevel level, BlockPos origin) {
        BlockState floor = Blocks.STONE.defaultBlockState();
        for (int z = 0; z < SIZE_Z; z++) {
            for (int x = 0; x < SIZE_X; x++) {
                level.setBlock(origin.offset(x, 0, z), floor, 3);
                level.setBlock(origin.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Destinations stay empty so undo has a verifiable air state to restore.
        for (int z = 0; z < SIZE_Z; z++) for (int x = 16; x < 32; x++)
            for (int y = 0; y < SIZE_Y; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        for (int z = 0; z < SIZE_Z; z++) for (int x = 0; x < SIZE_X; x++)
            for (int y = 10; y < 12; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        for (int z = 32; z < 48; z++) for (int x = 0; x < SIZE_X; x++)
            for (int y = 0; y < SIZE_Y; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);

        for (Case entry : CASES) {
            BlockPos pos = origin.offset(entry.x(), 1, entry.z());
            level.setBlock(pos, entry.state(), 3);
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null) throw new AssertionError("No block entity for " + entry.name() + " at " + pos);
            if (entry.loot() != null) {
                applyLoot((RandomizableContainerBlockEntity) entity, entry);
            } else {
                fill(entity, entry);
            }
            entity.setChanged();
        }

        level.setBlock(VOID_SUPPORT, floor, 3);
        level.setBlock(VOID_CHEST, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity voidEntity = level.getBlockEntity(VOID_CHEST);
        if (!(voidEntity instanceof Container container))
            throw new AssertionError("Void chest entity missing at setup");
        fillAll(container, "void_chest");
        voidEntity.setChanged();
    }

    private static void applyLoot(RandomizableContainerBlockEntity entity, Case entry) {
        Loot loot = entry.loot();
        entity.setLootTable(new ResourceLocation(loot.table()), loot.seed());
        // Unopened containers must stay lazy: no unpack call may run during setup.
        if (!loot.opened()) return;
        for (int attempt = 0; attempt < 32; attempt++) {
            entity.unpackLootTable(null);
            if (!entity.isEmpty()) return;
            entity.setLootTable(new ResourceLocation(loot.table()), loot.seed() + attempt + 1L);
        }
        throw new AssertionError("Loot table produced no items: " + entry.name());
    }

    private static void fill(BlockEntity entity, Case entry) {
        switch (entry.filler()) {
            case ALL -> fillAll((Container) entity, entry.name());
            case FURNACE -> {
                Container container = (Container) entity;
                set(container, 0, Items.DIRT, 12, entry.name());
                set(container, 1, Items.COAL, 5, entry.name());
                set(container, 2, entry.name().equals("blast_furnace") ? Items.GOLD_INGOT : Items.IRON_INGOT,
                        3, entry.name());
            }
            case BREWING -> {
                Container container = (Container) entity;
                for (int slot = 0; slot < 3; slot++) {
                    ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
                    container.setItem(slot, mark(potion, entry.name(), slot));
                }
                set(container, 3, Items.NETHER_WART, 4, entry.name());
                // Slot 4 stays empty: without blaze powder the stand never starts brewing.
                // Vanilla only refreshes HAS_BOTTLE during its tick; set it now so the captured
                // expected state matches the first tick instead of flipping a few ticks later.
                BlockState bottles = entity.getBlockState();
                for (int slot = 0; slot < 3; slot++) {
                    bottles = bottles.setValue(
                            net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[slot], true);
                }
                entity.getLevel().setBlock(entity.getBlockPos(), bottles, 3);
            }
            case BOOKS -> {
                Container container = (Container) entity;
                for (int slot = 0; slot < 6; slot++) set(container, slot, Items.BOOK, 1 + slot, entry.name());
            }
            case JUKEBOX -> {
                Container container = (Container) entity;
                set(container, 0, Items.MUSIC_DISC_13, 1, entry.name());
            }
            case CAMPFIRE -> {
                CampfireBlockEntity fire = (CampfireBlockEntity) entity;
                net.minecraft.world.item.Item[] food = {Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON};
                for (int slot = 0; slot < 4; slot++) {
                    fire.getItems().set(slot, mark(new ItemStack(food[slot], 1 + slot), entry.name(), slot));
                }
            }
            case LECTERN -> {
                LecternBlockEntity lectern = (LecternBlockEntity) entity;
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                CompoundTag tag = book.getOrCreateTag();
                tag.putString("title", "wepp container book");
                tag.putString("author", "WEPP");
                ListTag pages = new ListTag();
                pages.add(StringTag.valueOf("page one"));
                pages.add(StringTag.valueOf("page two"));
                tag.put("pages", pages);
                lectern.setBook(mark(book, entry.name(), 0));
            }
            default -> throw new AssertionError("Unhandled filler " + entry.filler());
        }
    }

    private static void fillAll(Container container, String name) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var items = new net.minecraft.world.item.Item[]{
                    Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
                    Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD
            };
            set(container, slot, items[slot % items.length], 1 + (slot * 7) % 64, name);
        }
    }

    private static void set(Container container, int slot, net.minecraft.world.item.Item item, int count, String name) {
        container.setItem(slot, mark(new ItemStack(item, count), name, slot));
    }

    private static ItemStack mark(ItemStack stack, String name, int slot) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("wepp_container_case", name);
        tag.putInt("wepp_container_slot", slot);
        return stack;
    }

    static List<Cell> capture(ServerLevel level, BlockPos min, int sx, int sy, int sz) {
        List<Cell> cells = new ArrayList<>(sx * sy * sz);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockPos pos = min.offset(x, y, z).immutable();
                    BlockState state = level.getBlockState(pos);
                    BlockEntity entity = level.getBlockEntity(pos);
                    String loot = null;
                    List<ItemStack> items = List.of();
                    if (entity != null) {
                        CompoundTag tag = entity.saveWithoutMetadata();
                        if (tag.contains("LootTable", 8)) {
                            loot = tag.getString("LootTable");
                        } else {
                            items = itemsOf(entity);
                        }
                    }
                    cells.add(new Cell(pos, state, items, loot));
                }
            }
        }
        return cells;
    }

    static List<Cell> captureChest(ServerLevel level, BlockPos pos) {
        return capture(level, pos, 1, 1, 1);
    }

    static List<Cell> shifted(List<Cell> cells, int dx, int dy, int dz) {
        List<Cell> moved = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            moved.add(new Cell(cell.pos().offset(dx, dy, dz), cell.state(), cell.items(), cell.lootTable()));
        }
        return moved;
    }

    /** Duplicates every cell along +X, matching the production stretch-tile semantics. */
    static List<Cell> repeatedX(List<Cell> cells, int tile) {
        List<Cell> repeated = new ArrayList<>(cells.size() * 2);
        for (Cell cell : cells) {
            repeated.add(cell);
            repeated.add(new Cell(cell.pos().offset(tile, 0, 0), cell.state(), cell.items(), cell.lootTable()));
        }
        return repeated;
    }

    static void verify(ServerLevel level, List<Cell> expected, String phase) {
        for (Cell cell : expected) {
            BlockState actual = level.getBlockState(cell.pos());
            if (!actual.equals(cell.state())) {
                throw new AssertionError(phase + ": block state mismatch at " + cell.pos()
                        + " expected " + cell.state() + " but was " + actual);
            }
            if (cell.state().isAir()) continue;
            BlockEntity entity = level.getBlockEntity(cell.pos());
            if (cell.lootTable() != null) {
                if (entity == null) throw new AssertionError(phase + ": missing loot container at " + cell.pos());
                CompoundTag tag = entity.saveWithoutMetadata();
                if (!tag.contains("LootTable", 8) || !cell.lootTable().equals(tag.getString("LootTable"))) {
                    throw new AssertionError(phase + ": lazy loot table changed at " + cell.pos()
                            + " expected " + cell.lootTable() + " but was " + tag);
                }
                if (!cell.items().isEmpty()) throw new AssertionError(phase + ": expected cell has items and loot");
                continue;
            }
            if (cell.items().isEmpty()) continue;
            if (entity == null) throw new AssertionError(phase + ": missing block entity at " + cell.pos()
                    + " state=" + actual + " expected state=" + cell.state());
            List<ItemStack> actualItems = itemsOf(entity);
            if (actualItems.size() != cell.items().size()) {
                throw new AssertionError(phase + ": slot count mismatch at " + cell.pos());
            }
            for (int slot = 0; slot < actualItems.size(); slot++) {
                if (!ItemStack.matches(cell.items().get(slot), actualItems.get(slot))) {
                    throw new AssertionError(phase + ": item mismatch at " + cell.pos() + " slot " + slot
                            + " expected " + cell.items().get(slot) + " but was " + actualItems.get(slot));
                }
            }
        }
    }

    static void verifyAir(ServerLevel level, BlockPos min, int sx, int sy, int sz, String phase) {
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            BlockPos pos = min.offset(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) throw new AssertionError(phase + ": expected air at " + pos + " but was " + state);
        }
    }

    static void verifyNoItemEntities(ServerLevel level, String phase) {        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(-30_000_000, -512, -30_000_000, 30_000_000, 512, 30_000_000));
        if (!found.isEmpty()) {
            throw new AssertionError(phase + ": " + found.size() + " item entities exist, first at "
                    + found.get(0).blockPosition() + " with " + found.get(0).getItem());
        }
    }

    private static List<ItemStack> itemsOf(BlockEntity entity) {
        List<ItemStack> items = new ArrayList<>();
        if (entity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                items.add(container.getItem(slot).copy());
            }
        } else if (entity instanceof CampfireBlockEntity fire) {
            for (ItemStack stack : fire.getItems()) items.add(stack.copy());
        } else if (entity instanceof LecternBlockEntity lectern) {
            items.add(lectern.getBook().copy());
        }
        return items;
    }

    private ContainerStressFixture() {}
}
