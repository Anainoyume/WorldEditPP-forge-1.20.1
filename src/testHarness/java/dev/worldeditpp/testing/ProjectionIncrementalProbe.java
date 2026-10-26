package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionState;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Uses real client models and GPU buffers; test-only reflection does not extend production APIs. */
final class ProjectionIncrementalProbe {
    private static SelectionBlockProjectionState projection;
    private static Object interiorVbo;
    private static com.mojang.blaze3d.vertex.VertexBuffer interiorEntityVbo;
    private static int stage, ticks;
    private static boolean sawPartial;

    static boolean tick(Minecraft mc) throws Exception {
        if (projection == null) {
            projection = new SelectionBlockProjectionState();
            projection.setActive(true);
            int[] entries = new int[32 * 16 * 16 * 2];
            for (int i = 0; i < entries.length / 2; i++) entries[i * 2] = i;
            int chestCell = 2 + 32 * (2 + 16 * 2);
            var chestState = Blocks.CHEST.defaultBlockState();
            var chest = new net.minecraft.world.level.block.entity.ChestBlockEntity(new net.minecraft.core.BlockPos(2,2,2), chestState);
            chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,17));
            entries[2 * chestCell + 1] = 1;
            var snapshot = new SelectionProjectionAssembler.SnapshotData(81,0,0,0,32,16,16,
                    new int[]{Block.getId(Blocks.STONE.defaultBlockState()),Block.getId(chestState)}, entries,
                    List.of(com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt.capture(chestCell,chest,mc.level.registryAccess())));
            var apply = projection.getClass().getDeclaredMethod("applySnapshot", SelectionProjectionAssembler.SnapshotData.class);
            apply.setAccessible(true); apply.invoke(projection, snapshot);
            require(!projection.isReady(), "Snapshot must not synchronously build all meshes");
        }
        if (++ticks > 500) throw new AssertionError("Incremental projection did not finish");
        projection.tickBake(mc);
        var meshes = meshes(projection);
        var iterator = meshes.iterator();
        if (iterator.hasNext() && !projection.isReady()) sawPartial = true;
        if (!projection.isReady()) return false;
        if (stage == 0) {
            require(sawPartial, "Finished pages must be visible before the entire projection is ready");
            interiorVbo = firstVbo(projection);
            interiorEntityVbo = firstEntityVbo(projection);
            require(projection.stretchFace(1, true), "Expansion must succeed");
            require(!projection.isReady(), "New pages must be deferred");
            require(firstVbo(projection) == interiorVbo, "Stretch must immediately retain the interior GPU buffer");
            stage++;
            return false;
        }
        if (stage == 1) {
            require(projection.sizeX() == 64, "Stretch must add exactly one source tile");
            require(firstVbo(projection) == interiorVbo, "Expansion must not rebuild the interior");
            require(firstEntityVbo(projection) == interiorEntityVbo, "Expansion must not rebake interior entities");
            require(projection.stretchFace(0, false), "Negative face contraction must succeed");
            stage++;
            return false;
        }
        require(firstVbo(projection) == interiorVbo, "Contraction must reuse the unchanged interior");
        require(firstEntityVbo(projection) == interiorEntityVbo, "Contraction must reuse interior entity VBO");
        projection.reset();
        require(interiorEntityVbo.isInvalid(), "Reset must release the entity VBO");
        require(!meshes(projection).iterator().hasNext(), "Reset must retire all page resources");
        return true;
    }

    private static Iterable<?> meshes(SelectionBlockProjectionState state) throws Exception {
        var method = state.getClass().getDeclaredMethod("meshes"); method.setAccessible(true);
        return (Iterable<?>) method.invoke(state);
    }
    private static Object firstVbo(SelectionBlockProjectionState state) throws Exception {
        var mesh = meshes(state).iterator().next();
        var method = mesh.getClass().getDeclaredMethod("layerVbos"); method.setAccessible(true);
        return ((Map<?, ?>) method.invoke(mesh)).values().iterator().next();
    }
    private static com.mojang.blaze3d.vertex.VertexBuffer firstEntityVbo(SelectionBlockProjectionState state) throws Exception {
        var mesh = meshes(state).iterator().next();
        var method = mesh.getClass().getDeclaredMethod("blockEntityVbos"); method.setAccessible(true);
        var buffers = (Map<?, ?>) method.invoke(mesh);
        require(!buffers.isEmpty(), "Local entity cache must not depend on world camera distance");
        return (com.mojang.blaze3d.vertex.VertexBuffer)buffers.values().iterator().next();
    }
    static int blockEntityCount(SelectionBlockProjectionState state) throws Exception {
        int count = 0;
        for (var mesh : meshes(state)) {
            var method = mesh.getClass().getDeclaredMethod("blockEntities"); method.setAccessible(true);
            count += ((List<?>) method.invoke(mesh)).size();
        }
        return count;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
