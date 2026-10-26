package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.bridge.minecraft.client.ui.ClientFeedback;

import com.redawn.worldeditpp.client.selection.projection.ProjectionPage;
import com.redawn.worldeditpp.operation.task.TickBudget;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import org.joml.Vector3f;

/** Owns incremental mesh baking and all snapshot GPU resources. */
final class SelectionProjectionMesh implements AutoCloseable {
    public static volatile long statUploadNanos, statUploadBytes, statUploads;
    private int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private ProjectionClipboardCache cache;
    private ProjectionPage page;
    private int cellCursor;
    private final Map<Integer, BlockEntity> blockEntitiesByCell = new Int2ObjectOpenHashMap<>();
    private boolean bakeDirty;

    private int bakedCount;
    private final Map<RenderType, OwnedBufferBuilder> layerBuilders = new LinkedHashMap<>();
    private final ProjectionMeshBuffers scratch;
    private final Map<RenderType, VertexBuffer> layerVbos = new LinkedHashMap<>();
    private final Map<RenderType, VertexBuffer> layerVbosView = java.util.Collections.unmodifiableMap(layerVbos);
    private final List<ProjectionBlockEntity> blockEntities = new ArrayList<>();
    private final List<ProjectionBlockEntity> blockEntitiesView = java.util.Collections.unmodifiableList(blockEntities);
    private final Map<RenderType, VertexBuffer> blockEntityVbos = new LinkedHashMap<>();
    private final Map<RenderType, VertexBuffer> blockEntityVbosView = java.util.Collections.unmodifiableMap(blockEntityVbos);
    private SnapshotBlockView snapshotView;
    private final PoseStack bakePoseStack = new PoseStack();
    private static final Direction[] DIRECTIONS = Direction.values();
    private final RandomSource random = RandomSource.create(0);
    private final BlockPos.MutableBlockPos cursorPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
    private final Vector3f normal = new Vector3f();
    private int fastCubeCount;
    private int fullyOccludedCount;
    private boolean ready;
    private boolean terrainUploaded, entityCapture;
    private int entityCursor;

    record ProjectionBlockEntity(BlockEntity entity, int blockX, int blockY, int blockZ) {}

    SelectionProjectionMesh(ProjectionMeshBuffers scratch) { this.scratch = scratch; }

    void replace(ProjectionClipboardCache cache, ProjectionPage page, ClientLevel level,
                 int worldOriginX, int worldOriginY, int worldOriginZ) {
        close();
        this.cache = cache;
        this.page = page;
        minX = page.x(); minY = page.y(); minZ = page.z();
        sizeX = page.width(); sizeY = page.height(); sizeZ = page.depth();
        snapshotView = new SnapshotBlockView(cache, blockEntitiesByCell, minX, minY, minZ,
                sizeX, sizeY, sizeZ, level, worldOriginX, worldOriginY, worldOriginZ);
        bakeDirty = true;
    }

    ProjectionPage page() { return page; }
    boolean isReady() { return ready; }
    Map<RenderType, VertexBuffer> layerVbos() { return layerVbosView; }
    List<ProjectionBlockEntity> blockEntities() { return blockEntitiesView; }
    Map<RenderType, VertexBuffer> blockEntityVbos() { return blockEntityVbosView; }

    public void tickBake(Minecraft minecraft, TickBudget budget) {
        if (!bakeDirty || minecraft.level == null) return;
        if (!snapshotView.prepare(budget)) return;
        int volume = sizeX * sizeY * sizeZ;
        while (cellCursor < volume && budget.take()) {
            int cell = cellCursor++;
            BlockPos pos = blockPos(cell);
            BlockState state = snapshotView.getBlockState(pos);
            if (state.isAir()) continue;
            SelectionBlockEntityData data = cache.nbt(pos.getX(), pos.getY(), pos.getZ());
            if (data != null && state.hasBlockEntity()) {
                BlockPos entityPos = pos.immutable();
                BlockEntity entity = ProjectionBlockEntityNbt.restore(data, entityPos, state, minecraft.level.registryAccess());
                if (entity == null) throw new IllegalArgumentException("Invalid projection block entity");
                entity.setLevel(minecraft.level);
                blockEntitiesByCell.put(cell, entity);
                blockEntities.add(new ProjectionBlockEntity(entity, pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ));
            }
            bakeBlock(minecraft, cell, pos, state);
            bakedCount++;
        }
        // Upload one bounded spatial page, never a whole selection.
        if (cellCursor >= volume) {
            if (!terrainUploaded) {
                finishTerrain();
                terrainUploaded = true;
            }
            bakeBlockEntities(minecraft, budget);
        }
    }

    private BufferBuilder builderFor(RenderType layer) {
        var owner = layerBuilders.computeIfAbsent(layer, ignored -> scratch.newLayer());
        var builder = owner.buffer();
        if (!builder.building()) builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        return builder;
    }

    private void bakeBlock(Minecraft minecraft, int cell, BlockPos pos, BlockState state) {

        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            try {
                BufferBuilder fluidBuilder = builderFor(ItemBlockRenderTypes.getRenderLayer(fluidState));
                VertexConsumer fluidConsumer = new AlphaScalingVertexConsumer(
                        new SectionOriginVertexConsumer(fluidBuilder,
                                localSectionOrigin(pos.getX(), minX),
                                localSectionOrigin(pos.getY(), minY),
                                localSectionOrigin(pos.getZ(), minZ)),
                        SelectionBlockProjectionState.PROJECTION_ALPHA);
                minecraft.getBlockRenderer().renderLiquid(pos, snapshotView, fluidConsumer, state, fluidState);
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.warn("Projection fluid bake failed: {}", pos, exception);
                ClientFeedback.error("message.worldeditpp.projection.rejected");
            }
        }

        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var cached = scratch.models.get(state, model);
        // Test neighbours once, not once per render layer. Avoid allocating six BlockPos/arrays.
        int visibleFaces = 0;
        if (state.getBlock().getClass() == Block.class && fluidState.isEmpty()
                && state.isSolidRender(snapshotView, pos)) {
            fastCubeCount++;
            // Hot path for the common large-fill case. A full cube is hidden whenever the
            // neighbour has a full occlusion shape; BlockState caches that predicate.
            for (Direction direction : DIRECTIONS) {
                neighbour.setWithOffset(pos, direction);
                BlockState adjacent = snapshotView.getBlockState(neighbour);
                if (!adjacent.isSolidRender(snapshotView, neighbour)) visibleFaces |= 1 << direction.ordinal();
            }
        } else {
            for (Direction direction : DIRECTIONS) {
                neighbour.setWithOffset(pos, direction);
                if (Block.shouldRenderFace(state, snapshotView, pos, direction, neighbour)) visibleFaces |= 1 << direction.ordinal();
            }
        }
        var modelView = cached == null
                ? ClientPlatformBridge.get().blockModel(state, model, blockEntitiesByCell.get(cell))
                : null;
        long seed = cached == null ? state.getSeed(pos) : 0L;
        if (cached != null && visibleFaces == 0 && !cached.hasUnculledQuads()) {
            fullyOccludedCount++;
            return;
        }
        if (modelView != null && visibleFaces == 0) {
            boolean hasUnculled = false;
            for (RenderType layer : modelView.layers()) {
                random.setSeed(seed);
                if (!modelView.quads(null, random, layer).isEmpty()) {
                    hasUnculled = true;
                    break;
                }
            }
            if (!hasUnculled) {
                fullyOccludedCount++;
                return;
            }
        }

        bakePoseStack.pushPose();
        try {
            bakePoseStack.translate(
                    localCoordinate(pos.getX(), minX),
                    localCoordinate(pos.getY(), minY),
                    localCoordinate(pos.getZ(), minZ));

            if (cached != null) {
                for (var layer : cached.layers()) {
                    for (Direction direction : DIRECTIONS) {
                        if ((visibleFaces & (1 << direction.ordinal())) != 0) {
                            for (BakedQuad quad : layer.faces().get(direction.ordinal()))
                                putQuad(minecraft, state, pos, builderFor(layer.type()), bakePoseStack.last(), quad);
                        }
                    }
                    for (BakedQuad quad : layer.unculled())
                        putQuad(minecraft, state, pos, builderFor(layer.type()), bakePoseStack.last(), quad);
                }
            } else {
                for (RenderType layer : modelView.layers()) {
                    for (Direction direction : DIRECTIONS) {
                        if ((visibleFaces & (1 << direction.ordinal())) == 0) continue;
                        random.setSeed(seed);
                        for (BakedQuad quad : modelView.quads(direction, random, layer))
                            putQuad(minecraft, state, pos, builderFor(layer), bakePoseStack.last(), quad);
                    }
                    random.setSeed(seed);
                    for (BakedQuad quad : modelView.quads(null, random, layer))
                        putQuad(minecraft, state, pos, builderFor(layer), bakePoseStack.last(), quad);
                }
            }
        } finally {
            bakePoseStack.popPose();
        }
    }

    private void putQuad(Minecraft minecraft, BlockState state, BlockPos pos,
                         VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad) {
        int[] vertices = quad.getVertices();
        Vec3i faceNormal = quad.getDirection() != null ? quad.getDirection().getNormal() : Vec3i.ZERO;
        Vector3f transformedNormal = pose.normal().transform(normal.set(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ()));
        int tintIndex = quad.getTintIndex();
        int tint = -1;
        if (tintIndex >= 0) {
            try {
                tint = minecraft.getBlockColors().getColor(state, snapshotView, pos, tintIndex);
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.debug("Projection tint lookup failed: {} tint={}", pos, tintIndex, exception);
            }
        }

        int vertexCount = vertices.length / 8;
        for (int i = 0; i < vertexCount; i++) {
            float x = Float.intBitsToFloat(vertices[i * 8]);
            float y = Float.intBitsToFloat(vertices[i * 8 + 1]);
            float z = Float.intBitsToFloat(vertices[i * 8 + 2]);
            float u = Float.intBitsToFloat(vertices[i * 8 + 4]);
            float v = Float.intBitsToFloat(vertices[i * 8 + 5]);
            int vertexColor = vertices[i * 8 + 3];
            int red = vertexColor & 0xFF;
            int green = vertexColor >> 8 & 0xFF;
            int blue = vertexColor >> 16 & 0xFF;
            if (tint >= 0) {
                red = red * FastColor.ARGB32.red(tint) / 255;
                green = green * FastColor.ARGB32.green(tint) / 255;
                blue = blue * FastColor.ARGB32.blue(tint) / 255;
            }
            int color = FastColor.ARGB32.color((int) (SelectionBlockProjectionState.PROJECTION_ALPHA * 255.0F), red, green, blue);
            consumer.vertex(pose.pose(), x, y, z)
                    .color(color)
                    .uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(transformedNormal.x(), transformedNormal.y(), transformedNormal.z()).endVertex();
        }
    }

    private void finishTerrain() {
        try {
            for (Map.Entry<RenderType, OwnedBufferBuilder> entry : layerBuilders.entrySet()) {
                try {
                    BufferBuilder.RenderedBuffer mesh = entry.getValue().buffer().end();
                    if (mesh.drawState().vertexCount() > 0) {
                        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        try {
                            vbo.bind();
                            long uploadStart = System.nanoTime();
                            vbo.upload(mesh);
                            statUploadNanos += System.nanoTime() - uploadStart;
                            statUploadBytes += (long) mesh.drawState().vertexCount() * 32L;
                            statUploads++;
                            layerVbos.put(entry.getKey(), vbo);
                        } catch (RuntimeException exception) {
                            vbo.close();
                            throw exception;
                        } finally {
                            VertexBuffer.unbind();
                        }
                    } else {
                        mesh.release();
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Projection layer upload failed: " + entry.getKey(), exception);
                }
            }
        } finally {
            closeLayerBuilders();
        }
    }

    private void finishBake() {
        cellCursor = 0;
        bakeDirty = false;
        ready = true;
        snapshotView = null;
        blockEntitiesByCell.clear();
        WorldEditPlusPlus.LOGGER.debug("Projection baked: blocks={} fastCubes={} occluded={} layers={} blockEntities={}",
                bakedCount, fastCubeCount, fullyOccludedCount, layerVbos.size(), blockEntities.size());
    }

    private void bakeBlockEntities(Minecraft minecraft, TickBudget budget) {
        if (blockEntities.isEmpty()) { finishBake(); return; }
        AlphaBlendBufferSource source = scratch.entities;
        if (!entityCapture) { source.beginCapture(); entityCapture = true; }
        bakePoseStack.setIdentity();
        // The pending page exclusively owns scratch across ticks. Publishing only after both
        // terrain and entities finish avoids a transient per-frame BER fallback during loading.
        while (entityCursor < blockEntities.size() && budget.take()) {
            ProjectionBlockEntity pbe = blockEntities.get(entityCursor++);
            bakePoseStack.pushPose();
            try {
                bakePoseStack.translate(pbe.blockX(), pbe.blockY(), pbe.blockZ());
                ProjectionBlockEntityRenderer.render(minecraft.getBlockEntityRenderDispatcher(), pbe.entity(),
                        minecraft.getFrameTime(), bakePoseStack,
                        type -> new AlphaScalingVertexConsumer(source.getBuffer(type),
                                // Overlay layers (banner patterns) must reach alpha 1.0 so the dye
                                // base and patterns can cover the white banner cloth; the base
                                // layer keeps the projection alpha and the composite applies it.
                                ProjectionBlockEntityLayers.isOverlay(type)
                                        ? 1.0F : SelectionBlockProjectionState.PROJECTION_ALPHA));
            } catch (RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.warn("Projection block entity bake failed: {}",
                        pbe.entity().getBlockPos(), exception);
                ClientFeedback.error("message.worldeditpp.projection.rejected");
            } finally {
                bakePoseStack.popPose();
            }
        }
        if (entityCursor == blockEntities.size() && budget.take()) {
            blockEntityVbos.putAll(source.finishCapture());
            entityCapture = false;
            finishBake();
        }
    }

    private void closeVbos() {
        for (VertexBuffer vbo : layerVbos.values()) {
            vbo.close();
        }
        layerVbos.clear();
        for (VertexBuffer vbo : blockEntityVbos.values()) {
            vbo.close();
        }
        blockEntityVbos.clear();
    }

    private BlockPos blockPos(int cell) {
        return cursorPos.set(
                minX + SelectionBlocksPayload.cellX(cell, sizeX, sizeY),
                minY + SelectionBlocksPayload.cellY(cell, sizeX, sizeY),
                minZ + SelectionBlocksPayload.cellZ(cell, sizeX, sizeY));
    }

    static int localCoordinate(int worldCoordinate, int snapshotOrigin) {
        return worldCoordinate - snapshotOrigin;
    }

    static int localSectionOrigin(int worldCoordinate, int snapshotOrigin) {
        return (worldCoordinate & -16) - snapshotOrigin;
    }

    private void closeLayerBuilders() {
        for (OwnedBufferBuilder owner : layerBuilders.values()) owner.close();
        layerBuilders.clear();
    }

    @Override
    public void close() {
        if (entityCapture) scratch.entities.cancelCapture();
        entityCapture = false;
        entityCursor = 0;
        terrainUploaded = false;
        bakeDirty = false;
        cellCursor = 0;
        bakedCount = 0;
        fastCubeCount = 0;
        fullyOccludedCount = 0;
        ready = false;
        closeVbos();
        closeLayerBuilders();
        blockEntities.clear();
        blockEntitiesByCell.clear();
        cache = null;
        page = null;
        snapshotView = null;
    }
}
