package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.client.selection.projection.ProjectionLayout;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.bridge.minecraft.math.MinecraftBlockStateRotation;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Stores one template; stretching never copies blocks, NBT, palettes or vertex buffers. */
final class ProjectionClipboardCache {
    private ProjectionLayout layout;
    private SelectionProjectionAssembler.SnapshotData source;
    private final Int2IntOpenHashMap cells = new Int2IntOpenHashMap();
    private final Int2ObjectOpenHashMap<SelectionBlockEntityData> blockEntities = new Int2ObjectOpenHashMap<>();
    private BlockState[] palette;
    private int entryCursor, nbtCursor, paletteCursor;
    private long nbtBytes;
    private final ArrayDeque<Transform> transforms = new ArrayDeque<>();
    private record Transform(int axis, int turn, boolean mirror, DiscreteRotation orientation) {}

    void replace(SelectionProjectionAssembler.SnapshotData snapshot) {
        clear();
        source = snapshot;
        layout = new ProjectionLayout(snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ());
        palette = new BlockState[snapshot.palette().length];
        cells.defaultReturnValue(-1);
    }

    void clear() {
        cells.clear(); blockEntities.clear(); transforms.clear();
        layout = null; source = null; palette = null;
        entryCursor = nbtCursor = paletteCursor = transformCursor = 0; nbtBytes = 0;
    }

    boolean ready() {
        return source != null && entryCursor >= source.entries().length
                && nbtCursor >= source.blockEntities().size()
                && paletteCursor >= palette.length && transforms.isEmpty();
    }

    void tick(TickBudget budget) {
        if (source == null) return;
        while (paletteCursor < palette.length && budget.take()) {
            palette[paletteCursor] = Block.stateById(source.palette()[paletteCursor]);
            paletteCursor++;
        }
        while (entryCursor < source.entries().length && budget.take()) {
            cells.put(source.entries()[entryCursor], source.entries()[entryCursor + 1]);
            entryCursor += 2;
        }
        while (nbtCursor < source.blockEntities().size() && budget.take()) {
            var data = source.blockEntities().get(nbtCursor++);
            blockEntities.put(data.cell(), data);
            nbtBytes += data.compressedSize();
        }
        if (paletteCursor < palette.length || entryCursor < source.entries().length || nbtCursor < source.blockEntities().size()) return;
        while (!transforms.isEmpty() && budget.take()) {
            Transform transform = transforms.peek();
            // Transform only the palette, never the (possibly million-cell) template.
            int index = transformCursor++;
            BlockState original = Block.stateById(source.palette()[index]);
            palette[index] = transform.mirror
                    ? MinecraftBlockStateRotation.mirror(palette[index], original, transform.orientation, transform.axis)
                    : MinecraftBlockStateRotation.rotate(palette[index], original, transform.orientation, transform.axis, transform.turn);
            if (transformCursor == palette.length) { transforms.remove(); transformCursor = 0; }
        }
    }

    private int transformCursor;
    int sizeX() { return layout == null ? 0 : layout.size(0); }
    int sizeY() { return layout == null ? 0 : layout.size(1); }
    int sizeZ() { return layout == null ? 0 : layout.size(2); }
    int tile(int axis) { return layout == null ? 0 : layout.tile(axis); }

    BlockState block(int x, int y, int z) {
        int cell = layout.sourceCell(x, y, z);
        int index = cell < 0 ? -1 : cells.get(cell);
        return index < 0 ? Blocks.AIR.defaultBlockState() : palette[index];
    }

    SelectionBlockEntityData nbt(int x, int y, int z) {
        return blockEntities.get(layout.sourceCell(x, y, z));
    }

    boolean stretchFace(int face, boolean expand) {
        if (!ready() || face < 0 || face > 5) return false;
        long copies = layout.copiesAfterStretch(face / 2, expand);
        if (copies < 0 || copies * (source.entries().length / 2L) > SelectionProjectionLimits.MAX_BLOCKS
                || copies * source.blockEntities().size() > SelectionProjectionLimits.MAX_BLOCK_ENTITY_COUNT
                || copies * nbtBytes > SelectionProjectionLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES) return false;
        layout.stretch(face / 2, expand);
        return true;
    }

    boolean resize(boolean expand) {
        boolean changed = false;
        for (int axis = 0; axis < 3; axis++) {
            if (expand || layout.size(axis) >= layout.tile(axis) * 3) {
                changed |= stretchFace(axis * 2, expand);
                changed |= stretchFace(axis * 2 + 1, expand);
            }
        }
        return changed;
    }

    boolean rotate(int axis, int turn) { return transform(axis, turn, false); }
    boolean mirror(int axis) { return transform(axis, 0, true); }

    private boolean transform(int axis, int turn, boolean mirror) {
        if (layout == null || axis < 0 || axis > 2 || (!mirror && Math.abs(turn) != 1)) return false;
        layout.transform(axis, turn, mirror);
        transforms.add(new Transform(axis, turn, mirror, layout.orientation()));
        return true;
    }
}

