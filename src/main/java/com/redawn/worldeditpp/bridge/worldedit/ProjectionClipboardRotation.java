package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.math.ProjectionGridTransforms;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import java.util.HashMap;
import java.util.Map;

/** Position and state rotation for the server's current/original clipboard pair. */
public final class ProjectionClipboardRotation {
    public static Result rotate(
            Clipboard current,
            Clipboard original,
            DiscreteRotation finalRotation,
            int axis,
            int quarterTurn
    ) {
        return transform(current, original, finalRotation, axis, quarterTurn, false);
    }

    public static Result mirror(
            Clipboard current, Clipboard original, DiscreteRotation finalTransform, int axis
    ) {
        if (axis < 0 || axis > 2) throw new IllegalArgumentException("Invalid mirror axis");
        return transform(current, original, finalTransform, axis, 0, true);
    }

    private static Result transform(
            Clipboard current, Clipboard original, DiscreteRotation finalRotation,
            int axis, int quarterTurn, boolean mirror
    ) {
        BlockVector3 oldDimensions = current.getDimensions();
        BlockVector3 newDimensions = mirror ? oldDimensions : switch (axis) {
            case 0 -> BlockVector3.at(oldDimensions.getX(), oldDimensions.getZ(), oldDimensions.getY());
            case 1 -> BlockVector3.at(oldDimensions.getZ(), oldDimensions.getY(), oldDimensions.getX());
            case 2 -> BlockVector3.at(oldDimensions.getY(), oldDimensions.getX(), oldDimensions.getZ());
            default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
        };
        BlockArrayClipboard rotatedCurrent = ClipboardBuffers.empty(newDimensions);
        BlockArrayClipboard rotatedOriginal = ClipboardBuffers.empty(newDimensions);
        Map<StatePair, BlockState> transformedStates = new HashMap<>();

        for (int z = 0; z < oldDimensions.getZ(); z++) {
            for (int y = 0; y < oldDimensions.getY(); y++) {
                for (int x = 0; x < oldDimensions.getX(); x++) {
                    BlockVector3 sourcePosition = BlockVector3.at(x, y, z);
                    BaseBlock currentBlock = current.getFullBlock(sourcePosition);
                    BaseBlock originalBlock = original.getFullBlock(sourcePosition);
                    if (currentBlock.getBlockType().getMaterial().isAir()
                            && originalBlock.getBlockType().getMaterial().isAir()) {
                        continue;
                    }

                    int[] targetCoordinates = mirror
                            ? ProjectionGridTransforms.mirrorCoordinates(new int[]{x, y, z}, axis,
                                    oldDimensions.getX(), oldDimensions.getY(), oldDimensions.getZ())
                            : ProjectionGridTransforms.rotateCoordinates(new int[]{x, y, z}, axis, quarterTurn,
                                    oldDimensions.getX(), oldDimensions.getY(), oldDimensions.getZ());
                    BlockVector3 targetPosition = BlockVector3.at(
                            targetCoordinates[0], targetCoordinates[1], targetCoordinates[2]);

                    if (!currentBlock.getBlockType().getMaterial().isAir()) {
                        BlockState currentState = currentBlock.toImmutableState();
                        BlockState originalState = originalBlock.toImmutableState();
                        StatePair pair = new StatePair(currentState, originalState);
                        BlockState transformedState = transformedStates.computeIfAbsent(pair,
                                ignored -> mirror
                                        ? ProjectionBlockStateRotation.mirror(currentState, originalState, finalRotation, axis)
                                        : ProjectionBlockStateRotation.rotate(currentState, originalState, finalRotation, axis, quarterTurn));
                        setBlock(rotatedCurrent, targetPosition, transformedState.toBaseBlock(currentBlock.getNbtData()));
                    }
                    if (!originalBlock.getBlockType().getMaterial().isAir()) {
                        setBlock(rotatedOriginal, targetPosition, originalBlock);
                    }
                }
            }
        }
        return new Result(rotatedCurrent, rotatedOriginal);
    }

    private static void setBlock(BlockArrayClipboard clipboard, BlockVector3 position, BaseBlock block) {
        try {
            clipboard.setBlock(position, block);
        } catch (com.sk89q.worldedit.WorldEditException exception) {
            throw new IllegalStateException("Cannot transform clipboard", exception);
        }
    }

    public record Result(BlockArrayClipboard current, BlockArrayClipboard original) { }

    private record StatePair(BlockState current, BlockState original) { }

    private ProjectionClipboardRotation() { }
}
