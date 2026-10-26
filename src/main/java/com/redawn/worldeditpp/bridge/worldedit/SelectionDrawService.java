package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.item.BlockStateItemData;
import com.redawn.worldeditpp.bridge.minecraft.item.DrawMaterialItems;
import com.redawn.worldeditpp.mode.DrawMode;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.regions.Region;
import com.redawn.worldeditpp.bridge.worldedit.operation.EditSessionTuning;
import com.redawn.worldeditpp.bridge.worldedit.operation.DrawSetJob;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;


/** Applies the player's held blocks to the ordinary WorldEdit selection while draw is held. */
public final class SelectionDrawService {
    public static void applyHeldBlocks(
            ServerPlayer player, Direction clickedFace, boolean clickedUpperHalf, DrawMode mode) {
        if (!EditModeManager.isEditing(player)) return;

        DrawSetJob incremental = DrawSetJob.tryOpen(player, clickedFace, clickedUpperHalf, mode);
        if (incremental != null) {
            if (!IncrementalJobScheduler.instance().register(incremental)) {
                incremental.close();
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.worldeditpp.history.busy"),
                        true);
            }
            return;
        }

        RegionSelection selection = WorldEditSelectionService.currentSelection(player).orElse(null);
        if (selection == null) return;
        if (mode == DrawMode.WALLS && !selection.kind().supportsCuboidOverlay()) return;
        Region region = selection.region();
        DrawMaterial source = resolveDrawMaterial(
                player.getItemInHand(InteractionHand.OFF_HAND), player.level().registryAccess()).orElse(null);
        DrawMaterial replacement = resolveDrawMaterial(
                player.getItemInHand(InteractionHand.MAIN_HAND), player.level().registryAccess()).orElse(null);
        if (region == null || replacement == null) return;

        if (!replacement.exactState()) {
            replacement = replacement.withState(orientForSet(
                    replacement.state(),
                    player.getDirection(),
                    Direction.orderedByNearest(player)[0],
                    clickedFace,
                    clickedUpperHalf));
        }
        var actor = WorldEditPlatform.adaptPlayer(player);
        LocalSession localSession = WorldEditSessions.of(player);
        EditSession editSession = localSession.createEditSession(actor);
        try {
            int affected = applyShape(editSession, region, mode, source, replacement);
            if (source == null) {
                actor.printInfo(mode == DrawMode.WALLS
                        ? TranslatableComponent.of("worldedit.walls.changed", TextComponent.of(affected))
                        : TranslatableComponent.of("worldedit.set.done"));
            } else {
                actor.printInfo(TranslatableComponent.of(
                        "worldedit.replace.replaced", TextComponent.of(affected)));
            }
            WorldEditPlusPlus.LOGGER.debug("Draw operation changed {} blocks for {}",
                    affected, player.getGameProfile().getName());
        } catch (WorldEditException | RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to apply held blocks to the selection", exception);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.worldeditpp.draw.failed"), true);
        } finally {
            rememberAndClose(localSession, editSession);
        }
    }

    /** Shape chooses the candidate region; the offhand independently supplies an optional filter. */
    public static int applyShape(EditSession edit, Region selection, DrawMode mode,
                                 DrawMaterial source, DrawMaterial replacement) throws WorldEditException {
        Region candidates = SelectionDrawShapes.region(selection, mode);
        if (source != null) return replaceBlocks(edit, candidates, source, replacement);
        if (replacement.exactState()) {
            EditSessionTuning.suppressBlockUpdates(edit);
        }
        return edit.setBlocks(candidates, replacement.toBaseBlock());
    }

    /** Applies the same directional properties used by vanilla stairs and rotated pillars. */
    public static BlockState orientForSet(
            BlockState state,
            Direction playerHorizontalDirection,
            Direction playerLookDirection,
            Direction clickedFace,
            boolean clickedUpperHalf
    ) {
        Property<?> facing = WorldEditBlockStates.property(state.getBlockType(), "facing");
        Block block = WorldEditPlatform.adapt(state.getBlockType());
        Object facingValue = facing == null
                ? null
                : WorldEditBlockStates.parse(facing, placementFacing(
                        block, facing, playerHorizontalDirection, playerLookDirection, clickedFace).getSerializedName());
        if (facingValue != null) state = WorldEditBlockStates.with(state, facing, facingValue);

        Property<?> axis = WorldEditBlockStates.property(state.getBlockType(), "axis");
        Object axisValue = axis == null ? null : WorldEditBlockStates.parse(axis, axisName(clickedFace.getAxis()));
        if (axisValue != null) state = WorldEditBlockStates.with(state, axis, axisValue);

        String verticalHalf = placementVerticalHalf(clickedFace, clickedUpperHalf);
        if (block instanceof StairBlock) {
            state = withNamedProperty(state, "half", verticalHalf);
        } else if (block instanceof SlabBlock) {
            state = withNamedProperty(state, "type", verticalHalf);
        }
        return state;
    }

    private static String placementVerticalHalf(Direction clickedFace, boolean clickedUpperHalf) {
        return switch (clickedFace) {
            case UP -> "bottom";
            case DOWN -> "top";
            default -> clickedUpperHalf ? "top" : "bottom";
        };
    }

    private static BlockState withNamedProperty(BlockState state, String propertyName, String valueName) {
        Property<?> property = WorldEditBlockStates.property(state.getBlockType(), propertyName);
        return property == null ? state : WorldEditBlockStates.withNamed(state, property, valueName);
    }

    private static Direction placementFacing(
            Block block,
            Property<?> facing,
            Direction horizontal,
            Direction look,
            Direction clickedFace
    ) {
        if (block instanceof StairBlock
                || block instanceof BedBlock
                || block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof CampfireBlock
                || block instanceof CalibratedSculkSensorBlock
                || block instanceof DecoratedPotBlock) {
            return horizontal;
        }
        if (block instanceof AnvilBlock) return horizontal.getClockWise();
        if (block instanceof ObserverBlock) return look;
        if (block instanceof HopperBlock) {
            Direction direction = clickedFace.getOpposite();
            return direction.getAxis() == Direction.Axis.Y ? Direction.DOWN : direction;
        }
        if (block instanceof ShulkerBoxBlock
                || block instanceof EndRodBlock
                || block instanceof LightningRodBlock
                || block instanceof AmethystClusterBlock) {
            return clickedFace;
        }
        if (block instanceof BellBlock) {
            return clickedFace.getAxis() == Direction.Axis.Y ? horizontal : clickedFace.getOpposite();
        }
        if (block instanceof TrapDoorBlock) {
            return clickedFace.getAxis().isHorizontal() ? clickedFace : horizontal.getOpposite();
        }
        if (block instanceof CocoaBlock) {
            return clickedFace.getAxis().isHorizontal() ? clickedFace.getOpposite() : horizontal.getOpposite();
        }
        if (isWallMounted(block)) {
            return clickedFace.getAxis().isHorizontal() ? clickedFace : horizontal.getOpposite();
        }

        boolean supportsVerticalFacing = WorldEditBlockStates.parse(facing, "up") != null
                && WorldEditBlockStates.parse(facing, "down") != null;
        return supportsVerticalFacing ? look.getOpposite() : horizontal.getOpposite();
    }

    private static boolean isWallMounted(Block block) {
        return block instanceof FaceAttachedHorizontalDirectionalBlock
                || block instanceof LadderBlock
                || block instanceof BaseCoralWallFanBlock
                || block instanceof WallBannerBlock
                || block instanceof WallSignBlock
                || block instanceof WallSkullBlock
                || block instanceof WallTorchBlock
                || block instanceof TripWireHookBlock;
    }

    private static String axisName(Direction.Axis axis) {
        return switch (axis) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
        };
    }

    /** WorldEdit-level replace operation exposed for dedicated-server regression tests. */
    public static int replaceBlocks(
            EditSession editSession,
            Region region,
            BlockType sourceType,
            BlockState replacement) throws WorldEditException {
        return replaceBlocks(
                editSession,
                region,
                DrawMaterial.plain(sourceType.getDefaultState()),
                DrawMaterial.plain(replacement));
    }

    /** Replaces cells using optional exact source state/NBT matching and exact target data. */
    public static int replaceBlocks(
            EditSession editSession,
            Region region,
            DrawMaterial source,
            DrawMaterial replacement) throws WorldEditException {
        EditSessionTuning.suppressBlockUpdates(editSession);
        BlockType sourceType = source.state().getBlockType();
        BlockType replacementType = replacement.state().getBlockType();
        List<PropertyPair> preservedProperties = replacement.exactState()
                ? List.of()
                : preservedProperties(sourceType, replacementType);
        boolean recalculateConnections = usesVanillaConnections(sourceType, replacementType);
        List<BlockVector3> replacedPositions = recalculateConnections ? new ArrayList<>() : List.of();
        int affected = 0;
        for (var position : region) {
            BlockState current = editSession.getBlock(position);
            if (!current.getBlockType().equals(sourceType)) continue;
            if (source.exactState() && !current.equals(source.state())) continue;
            if (source.blockEntityNbt() != null
                    && !blockEntityNbtMatches(editSession.getFullBlock(position), source.blockEntityNbt())) {
                continue;
            }
            if (recalculateConnections) replacedPositions.add(position);

            BlockState cellReplacement = replacement.state();
            for (PropertyPair properties : preservedProperties) {
                Object sourceValue = WorldEditBlockStates.value(current, properties.source());
                Object targetValue = sourceValue == null
                        ? null
                        : properties.mapValue(sourceValue);
                if (targetValue != null) {
                    cellReplacement = WorldEditBlockStates.with(cellReplacement, properties.target(), targetValue);
                }
            }
            if (editSession.setBlock(position, replacement.toBaseBlock(cellReplacement))) affected++;
        }
        if (recalculateConnections && !replacedPositions.isEmpty()) {
            editSession.flushSession();
            recalculateVanillaConnections(editSession, replacedPositions, !replacement.exactState());
        }
        return affected;
    }

    /** Maps every item accepted by draw-mode set or replace to its effective block state. */
    public static Optional<DrawMaterial> resolveDrawMaterial(ItemStack stack) {
        return resolveDrawMaterial(stack, null);
    }

    public static Optional<DrawMaterial> resolveDrawMaterial(
            ItemStack stack, HolderLookup.Provider registries) {
        return DrawMaterialItems.block(stack).map(block -> {
            var storedState = BlockStateItemData.read(stack);
            net.minecraft.world.level.block.state.BlockState nativeState = storedState
                    .map(BlockStateItemData.StoredState::state)
                    .orElseGet(block::defaultBlockState);
            com.sk89q.jnbt.CompoundTag blockEntityNbt = registries == null
                    ? null
                    : readBlockEntityNbt(stack, nativeState, registries);
            return new DrawMaterial(WorldEditPlatform.adapt(nativeState), blockEntityNbt, storedState.isPresent());
        });
    }

    private static com.sk89q.jnbt.CompoundTag readBlockEntityNbt(
            ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state,
            HolderLookup.Provider registries
    ) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
        if (blockEntity == null) return null;

        CompoundTag before = normalizedBlockEntityNbt(blockEntity.saveWithFullMetadata());
        CompoundTag customData = net.minecraft.world.item.BlockItem.getBlockEntityData(stack);
        if (customData != null) blockEntity.load(customData.copy());
        CompoundTag after = normalizedBlockEntityNbt(blockEntity.saveWithFullMetadata());
        return after.equals(before) ? null : WorldEditPlatform.fromNative(after);
    }

    private static boolean blockEntityNbtMatches(BaseBlock block, com.sk89q.jnbt.CompoundTag expected) {
        if (block.getNbtData() == null) return false;
        CompoundTag actual = WorldEditPlatform.toNative(block.getNbtData()).copy();
        return normalizedBlockEntityNbt(actual).equals(normalizedBlockEntityNbt(WorldEditPlatform.toNative(expected)));
    }

    private static CompoundTag normalizedBlockEntityNbt(CompoundTag tag) {
        CompoundTag normalized = tag.copy();
        normalized.remove("x");
        normalized.remove("y");
        normalized.remove("z");
        return normalized;
    }

    private static void rememberAndClose(LocalSession localSession, EditSession editSession) {
        com.redawn.worldeditpp.bridge.worldedit.operation.EditSessionLifecycle.rememberAndClose(localSession, editSession);
    }

    private static void recalculateVanillaConnections(
            EditSession editSession,
            List<BlockVector3> replacedPositions,
            boolean recalculateReplacements
    ) throws WorldEditException {
        ServerLevel level = WorldEditPlatform.adapt(editSession.getWorld());
        Set<BlockPos> candidates = new LinkedHashSet<>();
        Set<BlockPos> replacements = new LinkedHashSet<>();
        for (BlockVector3 position : replacedPositions) {
            BlockPos nativePosition = WorldEditPlatform.toBlockPos(position);
            replacements.add(nativePosition);
            if (recalculateReplacements) candidates.add(nativePosition);
            for (Direction direction : Direction.values()) {
                candidates.add(nativePosition.relative(direction));
            }
        }
        if (!recalculateReplacements) candidates.removeAll(replacements);

        List<BlockPos> orderedCandidates = candidates.stream()
                .filter(position -> isVanillaConnectionBlock(level.getBlockState(position).getBlock()))
                .sorted(Comparator.comparingInt((BlockPos position) -> position.getY()).reversed())
                .toList();
        for (int pass = 0; pass < 2; pass++) {
            boolean changed = false;
            for (BlockPos position : orderedCandidates) {
                net.minecraft.world.level.block.state.BlockState current = level.getBlockState(position);
                net.minecraft.world.level.block.state.BlockState updated =
                        Block.updateFromNeighbourShapes(current, level, position);
                if (!updated.equals(current)) {
                    editSession.setBlock(WorldEditPlatform.adapt(position), WorldEditPlatform.adapt(updated));
                    changed = true;
                }
            }
            if (!changed) break;
            editSession.flushSession();
        }
    }

    private static boolean usesVanillaConnections(BlockType sourceType, BlockType targetType) {
        return isVanillaConnectionBlock(WorldEditPlatform.adapt(sourceType))
                || isVanillaConnectionBlock(WorldEditPlatform.adapt(targetType));
    }

    private static boolean isVanillaConnectionBlock(Block block) {
        return block instanceof WallBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock;
    }

    private static List<PropertyPair> preservedProperties(BlockType sourceType, BlockType targetType) {
        List<String> names = new ArrayList<>();
        names.add("facing");

        var sourceBlock = WorldEditPlatform.adapt(sourceType);
        var targetBlock = WorldEditPlatform.adapt(targetType);
        if (sourceBlock instanceof StairBlock && targetBlock instanceof StairBlock) {
            names.add("half");
            names.add("shape");
        }
        if (sourceBlock instanceof SlabBlock && targetBlock instanceof SlabBlock) {
            names.add("type");
        }
        if (sourceBlock instanceof RotatedPillarBlock && targetBlock instanceof RotatedPillarBlock) {
            names.add("axis");
        }
        if (sourceBlock instanceof IronBarsBlock && targetBlock instanceof IronBarsBlock) {
            names.add("east");
            names.add("north");
            names.add("south");
            names.add("west");
        }

        return names.stream()
                .map(name -> propertyPair(sourceType, targetType, name))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PropertyPair> propertyPair(
            BlockType sourceType, BlockType targetType, String name) {
        Property<?> source = WorldEditBlockStates.property(sourceType, name);
        Property<?> target = WorldEditBlockStates.property(targetType, name);
        return source == null || target == null
                ? Optional.empty()
                : Optional.of(new PropertyPair(source, target));
    }

    private record PropertyPair(Property<?> source, Property<?> target) {
        Object mapValue(Object sourceValue) {
            return WorldEditBlockStates.parse(target, sourceValue.toString());
        }
    }

    public record DrawMaterial(BlockState state, com.sk89q.jnbt.CompoundTag blockEntityNbt, boolean exactState) {
        public static DrawMaterial plain(BlockState state) {
            return new DrawMaterial(state, null, false);
        }

        public DrawMaterial withState(BlockState updatedState) {
            return new DrawMaterial(updatedState, blockEntityNbt, exactState);
        }

        public BaseBlock toBaseBlock() {
            return toBaseBlock(state);
        }

        public BaseBlock toBaseBlock(BlockState targetState) {
            return blockEntityNbt == null
                    ? targetState.toBaseBlock()
                    : targetState.toBaseBlock(
                            blockEntityNbt);
        }
    }

    private SelectionDrawService() { }
}
