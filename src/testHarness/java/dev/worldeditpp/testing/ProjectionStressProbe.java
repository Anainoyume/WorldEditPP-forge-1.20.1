package dev.worldeditpp.testing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.AlphaBlendBufferSource;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionState;
import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Real GPU and jemalloc regression: repeated page cancellation and the bed-renderer OOM path. */
final class ProjectionStressProbe {
    private static final SelectionBlockProjectionState projection = new SelectionBlockProjectionState();
    private static final List<String> results = new ArrayList<>();
    private static int stage, ticks, frames, events;
    private static long buildNanos, maxTickNanos, start;
    private static AlphaBlendBufferSource buffers;
    private static List<BlockEntity> entities;
    private static Map<?, ?> warmedBuffers;
    private static Map<?, ?> warmedVbos;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 3500) throw new AssertionError("Projection stress timed out at " + stage);
        if (stage == 0) {
            nativeReleaseProbe();
            results.add("Stone baked model=" + mc.getBlockRenderer()
                    .getBlockModel(Blocks.STONE.defaultBlockState()).getClass().getName());
            apply(solid(120, 64, 64));
            start = System.nanoTime();
            stage = 1;
        }
        long before = System.nanoTime();
        projection.tickBake(mc);
        long elapsed = System.nanoTime() - before;
        buildNanos += elapsed;
        maxTickNanos = Math.max(maxTickNanos, elapsed);
        if (stage == 1 && projection.isReady()) {
            results.add("491520 solid blocks: ticks=" + ticks + ", wall_ms=" + (System.nanoTime()-start)/1e6
                    + ", bake_ms=" + buildNanos/1e6 + ", max_call_ms=" + maxTickNanos/1e6);
            require(projection.stretchFace(1, true), "Near-million expansion rejected");
            stage = 2; start = System.nanoTime(); buildNanos = 0; ticks = 0;
        } else if (stage == 2 && projection.isReady()) {
            results.add("983040 expanded blocks: ticks=" + ticks + ", wall_ms=" + (System.nanoTime()-start)/1e6
                    + ", incremental_bake_ms=" + buildNanos/1e6);
            projection.reset();
            apply(mixed(mc));
            stage = 3; ticks = 0;
            buffers = new AlphaBlendBufferSource();
        } else if (stage == 3 && projection.isReady()) {
            renderFrame(mc);
            warmedBuffers = new LinkedHashMap<>((Map<?, ?>) field(buffers, "buffers"));
            warmedVbos = new LinkedHashMap<>((Map<?, ?>) field(buffers, "streaming"));
            stage = 4;
        } else if (stage == 4) {
            for (int i = 0; i < 10; i++) {
                require(projection.stretchFace(1, true), "Rapid expansion rejected");
                projection.tickBake(mc);
                require(projection.stretchFace(1, false), "Rapid contraction rejected");
                events += 2;
                renderFrame(mc);
                frames++;
            }
            if (frames >= 1000) { stage = 5; ticks = 0; }
        } else if (stage == 5 && projection.isReady()) {
            Map<?, ?> actual = (Map<?, ?>) field(buffers, "buffers");
            require(actual.equals(warmedBuffers), "Entity builders were replaced each frame");
            require(((Map<?, ?>) field(buffers,"streaming")).equals(warmedVbos), "Streaming entity VBOs were replaced each frame");
            long retained = ((Number) invoke(buffers, "bytes")).longValue();
            require(retained < 16L*1024*1024, "Entity scratch grew beyond bounded batches");
            results.add("Rapid resize: events=" + events + ", bed/chest frames=" + frames
                    + ", retained_entity_bytes=" + retained + ", builder_instances=" + actual.size());
            Object pages = field(projection, "mesh");
            Object scratch = field(pages, "scratch");
            results.add("Mesh scratch bytes after churn=" + invoke(scratch, "bytes"));
            projection.reset();
            require(((Number) invoke(scratch, "bytes")).longValue() == 0, "Mesh native memory retained after reset");
            List<?> owners = new ArrayList<>(actual.values());
            buffers.close();
            for (Object vbo : warmedVbos.values()) require(((com.mojang.blaze3d.vertex.VertexBuffer)vbo).isInvalid(), "Closed streaming VBO retained GPU storage");
            for (Object owner : owners) require(((Number) invoke(owner, "bytes")).longValue() == 0, "Closed entity buffer still owns memory");
            Files.write(Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling("projection-performance.txt"), results);
            return true;
        }
        return false;
    }

    private static SelectionProjectionAssembler.SnapshotData solid(int x, int y, int z) {
        int[] entries = new int[x*y*z*2];
        for (int i = 0; i < entries.length/2; i++) entries[2*i] = i;
        return new SelectionProjectionAssembler.SnapshotData(91,0,0,0,x,y,z,
                new int[]{Block.getId(Blocks.STONE.defaultBlockState())}, entries, List.of());
    }
    private static SelectionProjectionAssembler.SnapshotData mixed(Minecraft mc) {
        int[] entries = new int[16*2*16*2];
        for (int i = 0; i < entries.length/2; i++) entries[2*i] = i;
        var bedFoot = Blocks.RED_BED.defaultBlockState();
        var bedHead = bedFoot.setValue(net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD);
        var chest = Blocks.CHEST.defaultBlockState();
        var nbt = new ArrayList<SelectionBlockEntityData>();
        entities = new ArrayList<>();
        for (int x = 0; x < 16; x += 4) for (int z = 0; z < 16; z += 4) {
            int cell = x + 16 + 32*z;
            BlockPos pos = new BlockPos(x, 1, z);
            var bed = new BedBlockEntity(pos, bedFoot); bed.setLevel(mc.level);
            var head = new BedBlockEntity(pos.north(), bedHead); head.setLevel(mc.level);
            var box = new ChestBlockEntity(pos.east(), chest); box.setLevel(mc.level);
            entities.add(bed); entities.add(head); entities.add(box);
            entries[2*cell+1] = 1; entries[2*(cell+1)+1] = 3;
            nbt.add(ProjectionBlockEntityNbt.capture(cell, bed, mc.level.registryAccess()));
            nbt.add(ProjectionBlockEntityNbt.capture(cell+1, box, mc.level.registryAccess()));
        }
        return new SelectionProjectionAssembler.SnapshotData(92,0,0,0,16,2,16,
                new int[]{Block.getId(Blocks.STONE.defaultBlockState()),Block.getId(bedFoot),Block.getId(bedHead),Block.getId(chest)},
                entries, nbt);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderFrame(Minecraft mc) {
        PoseStack pose = new PoseStack();
        for (BlockEntity entity : entities) {
            BlockEntityRenderer renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
            require(renderer != null, "Missing real bed/chest renderer");
            renderer.render(entity, 0, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        buffers.endBatch();
    }
    private static void apply(SelectionProjectionAssembler.SnapshotData snapshot) throws Exception {
        projection.setActive(true);
        var method = projection.getClass().getDeclaredMethod("applySnapshot",SelectionProjectionAssembler.SnapshotData.class);
        method.setAccessible(true);
        require((Boolean) method.invoke(projection,snapshot), "Invalid stress snapshot");
    }
    private static void nativeReleaseProbe() throws Exception {
        var type = Class.forName("com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.OwnedBufferBuilder");
        var ctor = type.getDeclaredConstructor(int.class); ctor.setAccessible(true);
        var owners = new ArrayList<AutoCloseable>();
        for (int i = 0; i < 16; i++) owners.add((AutoCloseable) ctor.newInstance(262144));
        long allocated = 0;
        for (AutoCloseable owner : owners) allocated += ((Number) invoke(owner, "bytes")).longValue();
        for (AutoCloseable owner : owners) owner.close();
        for (AutoCloseable owner : owners) require(((Number) invoke(owner, "bytes")).longValue() == 0,
                "Closed owner still reports native storage");
        require(allocated >= 20L*1024*1024, "Native allocation probe was too small");
        results.add("Explicit native free exercised: allocated_bytes=" + allocated + ", closed_owners=" + owners.size());
    }
    private static Object field(Object object,String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invoke(Object object,String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
