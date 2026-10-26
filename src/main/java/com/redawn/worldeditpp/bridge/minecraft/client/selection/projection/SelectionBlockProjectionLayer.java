package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayContext;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayLayer;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionRotationRenderTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class SelectionBlockProjectionLayer implements SelectionOverlayLayer {
    private final Set<RenderType> entityLayers = new java.util.LinkedHashSet<>();
    private final ArrayList<RenderType> opaqueEntityLayers = new ArrayList<>();
    private final ArrayList<RenderType> overlayEntityLayers = new ArrayList<>();
    private final ArrayList<RenderType> orderedEntityLayers = new ArrayList<>();
    private final ProjectionDepthTargets depthTargets = new ProjectionDepthTargets();
    private final ProjectionCompositeTarget compositeTarget = new ProjectionCompositeTarget();
    private final ProjectionOcclusionCuller occlusion = new ProjectionOcclusionCuller();
    private final ArrayList<SelectionProjectionMesh> visibleMeshes = new ArrayList<>();
    private final ArrayList<SelectionProjectionMesh> colorMeshes = new ArrayList<>();
    private final Set<RenderType> visibleLayers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f frustumMatrix = new Matrix4f();
    private final Matrix4f modelViewMatrix = new Matrix4f();
    private final Matrix4f pageModelView = new Matrix4f();
    private final Matrix4f blockEntityModelView = new Matrix4f();
    private final PoseStack rotationStack = new PoseStack();
    private final ProjectionOcclusionCuller selfOcclusion = new ProjectionOcclusionCuller();
    /** Development comparison switches; production defaults keep the safe path. */
    static boolean EARLY_VISIBILITY = true;

    static boolean BACKFACE_CULLING = true;
    static boolean DISTANCE_CULLING = true;
    // 2026-10-23 experiment: draw the projection as an opaque offscreen layer and composite it
    // once at 0.6. -Dworldeditpp.render.opaqueComposite=false restores the two-pass path.
    static boolean OPAQUE_COMPOSITE =
            !"false".equalsIgnoreCase(System.getProperty("worldeditpp.render.opaqueComposite", "true"));
    // The projection is 0.6 translucent: a page behind a front page still contributes through
    // the alpha. Depth-based page culling is only valid for opaque output and disappears as soon
    // as the view stops changing (the query results become current). Keep the path for
    // experiments but never enable it by default.
    static boolean SELF_OCCLUSION = false;
    static boolean TRANSLATED_PAGES = false;
    public static volatile long statFrames, statVisiblePages, statDrawCalls, statBlockEntityDraws;
    public static volatile long statDepthCopies, statVisibleBlockEntities;
    private boolean depthDeferred;


    @Override
    public void render(SelectionOverlayContext context, SelectionOverlayState state) {
        if (depthDeferred) restoreDeferredDepth();
        SelectionBlockProjectionState projection = SelectionBlockProjectionController.state();
        if (!projection.isActive() || !projection.hasSnapshot()) {
            return;
        }

        AABB box = state.cuboidSelectionBox();
        Vec3 camera = context.camera().getPosition();
        boolean deferCloudDepth = context.minecraft().options.getCloudsType() != CloudStatus.OFF
                && context.minecraft().options.graphicsMode().get() != GraphicsStatus.FABULOUS;

        rotationStack.setIdentity();
        SelectionRotationRenderTransform.apply(rotationStack, state, camera);
        // Forge 1.20.1 AFTER_PARTICLES keeps camera rotation in the event PoseStack.
        Matrix4f modelView = modelViewMatrix.set(context.poseStack().last().pose())
                .mul(rotationStack.last().pose());
        Matrix4f projectionMatrix = context.projectionMatrix();
        frustum.set(frustumMatrix.set(projectionMatrix).mul(modelView));
        // A mirror transition scales one axis negatively and flips the quad winding; keep both
        // sides for that short animation, then cull normally.
        boolean cull = BACKFACE_CULLING && !state.isMirrorAnimating();
        double renderDistance = context.minecraft().options.renderDistance().get() * 16.0D;
        double maxDistanceSq = renderDistance * renderDistance;
        visibleMeshes.clear();
        visibleLayers.clear();
        occlusion.beginFrame(modelView, projectionMatrix, camera);
        for (SelectionProjectionMesh mesh : projection.meshes()) {
            var page = mesh.page();
            float x0 = cameraRelativeOrigin(box.minX + page.x(), camera.x);
            float y0 = cameraRelativeOrigin(box.minY + page.y(), camera.y);
            float z0 = cameraRelativeOrigin(box.minZ + page.z(), camera.z);
            float x1 = cameraRelativeOrigin(box.minX + page.x() + page.width(), camera.x);
            float y1 = cameraRelativeOrigin(box.minY + page.y() + page.height(), camera.y);
            float z1 = cameraRelativeOrigin(box.minZ + page.z() + page.depth(), camera.z);
            if (DISTANCE_CULLING && nearestDistanceSq(x0, y0, z0, x1, y1, z1) > maxDistanceSq) continue;
            if ((!mesh.layerVbos().isEmpty() || !mesh.blockEntities().isEmpty())
                    && frustum.testAab(x0, y0, z0, x1, y1, z1)
                    && occlusion.isVisible(page.key(), x0, y0, z0, x1, y1, z1)) {
                visibleMeshes.add(mesh);
                visibleLayers.addAll(mesh.layerVbos().keySet());
            }
        }
        // Front-to-back order lets the depth prepass reject more fragments through early-Z.
        visibleMeshes.sort((left, right) -> Double.compare(
                pageCenterDistanceSq(left, box, camera), pageCenterDistanceSq(right, box, camera)));
        occlusion.endFrame();
        statFrames++;
        statVisiblePages += visibleMeshes.size();

        RenderTarget mainTarget = context.minecraft().getMainRenderTarget();
        if (!EARLY_VISIBILITY) {
            // Legacy path: copy the full world depth before discovering that nothing is visible.
            if (!depthTargets.begin(mainTarget)) return;
            statDepthCopies++;
            if (visibleMeshes.isEmpty()) {
                depthTargets.restoreWorld(mainTarget);
                statDepthCopies++;
                return;
            }
        } else if (visibleMeshes.isEmpty()) {
            // Off-screen projection: no depth copies, no draws.
            return;
        }
        if (EARLY_VISIBILITY && OPAQUE_COMPOSITE) {
            int width = mainTarget.width, height = mainTarget.height;
            int[] rect = screenRect(box, camera, modelView, projectionMatrix, width, height);
            if (rect != null && compositeTarget.begin(mainTarget, width, height, rect)) {
                statDepthCopies++;
                boolean translucent = hasTranslucentLayer();
                try {
                    drawLayersOpaque(visibleMeshes, box, camera, modelView, projectionMatrix, cull);
                    renderBlockEntitiesOpaque(visibleMeshes, box, camera, modelView, projectionMatrix);
                } finally {
                    compositeTarget.composite(mainTarget, width, height, rect);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.disableBlend();
                }
                if (translucent || deferCloudDepth) {
                    boolean depthPrepared = false;
                    if (deferCloudDepth && depthTargets.begin(mainTarget)) {
                        // Keep projection depth in the main target until AFTER_WEATHER so the
                        // vanilla cloud pass (which runs between these stages) is occluded.
                        compositeTarget.copyDepthTo(mainTarget, rect);
                        depthDeferred = true;
                        depthPrepared = true;
                        statDepthCopies += 2;
                    }
                    if (translucent) {
                        drawTranslucentOverMain(mainTarget, width, height, rect, box, camera,
                                modelView, projectionMatrix, cull, depthPrepared);
                    }
                }
                return;
            }
            // Rect/level edge case: fall through to the proven two-pass path.
        }
        if (EARLY_VISIBILITY) {
            if (!depthTargets.begin(mainTarget)) return;
            statDepthCopies++;
        }
        try {
            drawLayers(visibleMeshes, box, camera, modelView, projectionMatrix, true, cull);
            colorMeshes.clear();
            if (SELF_OCCLUSION) {
                // After the depth prepass, an AABB query shows whether the page still has any
                // fragment in front of the accumulated projection depth. Fully covered pages
                // skip the blended colour pass and their block entities.
                selfOcclusion.beginFrame(modelView, projectionMatrix, camera);
                for (SelectionProjectionMesh mesh : visibleMeshes) {
                    var page = mesh.page();
                    float x0 = cameraRelativeOrigin(box.minX + page.x(), camera.x);
                    float y0 = cameraRelativeOrigin(box.minY + page.y(), camera.y);
                    float z0 = cameraRelativeOrigin(box.minZ + page.z(), camera.z);
                    float x1 = cameraRelativeOrigin(box.minX + page.x() + page.width(), camera.x);
                    float y1 = cameraRelativeOrigin(box.minY + page.y() + page.height(), camera.y);
                    float z1 = cameraRelativeOrigin(box.minZ + page.z() + page.depth(), camera.z);
                    if (selfOcclusion.isVisible(page.key(), x0, y0, z0, x1, y1, z1)) colorMeshes.add(mesh);
                }
                selfOcclusion.endFrame();
            } else {
                colorMeshes.addAll(visibleMeshes);
            }
            renderBlockEntities(state, visibleMeshes, box, camera, modelView, projectionMatrix, true);
            drawLayers(colorMeshes, box, camera, modelView, projectionMatrix, false, cull);
            renderBlockEntities(state, colorMeshes, box, camera, modelView, projectionMatrix, false);
        } finally {
            if (deferCloudDepth) {
                // Clouds are rendered between AFTER_PARTICLES and AFTER_WEATHER. Keep the
                // projection depth until the weather stage so cloud fragments behind the
                // projection fail the depth test; AFTER_WEATHER restores terrain depth.
                depthDeferred = true;
            } else {
                // Projection depth is temporary; later world/overlay passes see terrain again.
                depthTargets.restoreWorld(mainTarget);
                statDepthCopies++;
            }
        }
    }

    /** Restores terrain depth after the vanilla cloud pass has used the projection depth. */
    public void restoreDeferredDepth() {
        if (!depthDeferred) return;
        depthDeferred = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null && main.frameBufferId != -1) {
            depthTargets.restoreWorld(main);
            statDepthCopies++;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableBlend();
    }

    public void close() {
        restoreDeferredDepth();
        occlusion.close();
        selfOcclusion.close();
        depthTargets.close();
        compositeTarget.close();
        visibleMeshes.clear(); colorMeshes.clear(); visibleLayers.clear(); entityLayers.clear();
        opaqueEntityLayers.clear(); overlayEntityLayers.clear(); orderedEntityLayers.clear();
    }

    static float cameraRelativeOrigin(double targetOrigin, double cameraCoordinate) {
        return (float) (targetOrigin - cameraCoordinate);
    }

    /** Squared distance from the camera (origin in camera-relative space) to an AABB. */
    private static double nearestDistanceSq(double x0, double y0, double z0,
                                            double x1, double y1, double z1) {
        double dx = x0 > 0.0D ? x0 : (x1 < 0.0D ? -x1 : 0.0D);
        double dy = y0 > 0.0D ? y0 : (y1 < 0.0D ? -y1 : 0.0D);
        double dz = z0 > 0.0D ? z0 : (z1 < 0.0D ? -z1 : 0.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double pageCenterDistanceSq(SelectionProjectionMesh mesh, AABB box, Vec3 camera) {
        var page = mesh.page();
        double x = box.minX + page.x() + page.width() * 0.5D - camera.x;
        double y = box.minY + page.y() + page.height() * 0.5D - camera.y;
        double z = box.minZ + page.z() + page.depth() * 0.5D - camera.z;
        return x * x + y * y + z * z;
    }

    private void drawLayers(Iterable<SelectionProjectionMesh> meshes, AABB box, Vec3 camera,
                            Matrix4f modelView, Matrix4f projectionMatrix, boolean depthPass, boolean cull) {
        for (RenderType layer : visibleLayers) {
            drawLayer(layer, meshes, box, camera, modelView, projectionMatrix, depthPass, cull);
        }
    }

    private void drawLayer(RenderType layer, Iterable<SelectionProjectionMesh> meshes,
                           AABB box, Vec3 camera,
                           Matrix4f modelView, Matrix4f projectionMatrix, boolean depthPass,
                           boolean cull) {
        RenderType type = depthPass
                ? ProjectionRenderType.depthOf(layer, cull, TRANSLATED_PAGES)
                : ProjectionRenderType.blendOf(layer, cull);
        type.setupRenderState();
        if (cull) {
            // Some later passes (occlusion query boxes, clear-state paths) leave culling off.
            // Re-assert the face state explicitly before every page batch.
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
        } else {
            RenderSystem.disableCull();
        }
        ShaderInstance shader = RenderSystem.getShader();
        try {
            // Same contract as vanilla renderChunkLayer: bind uniforms/textures once, then
            // upload only the changing page origin. drawWithShader would clear them every page.
            ProjectionShader.begin(shader, modelView, projectionMatrix);
            for (SelectionProjectionMesh mesh : meshes) {
                VertexBuffer vbo = mesh.layerVbos().get(layer);
                if (vbo == null) continue;
                var page = mesh.page();
                float translateX = cameraRelativeOrigin(box.minX + page.x(), camera.x);
                float translateY = cameraRelativeOrigin(box.minY + page.y(), camera.y);
                float translateZ = cameraRelativeOrigin(box.minZ + page.z(), camera.z);
                vbo.bind();
                if (TRANSLATED_PAGES) {
                    // Both depth and colour use the same translated model-view, so their float
                    // transforms are identical and cannot z-fight. ChunkOffset stays zero, which
                    // also means the projection does not apply vanilla distance fog.
                    if (shader.MODEL_VIEW_MATRIX != null) {
                        shader.MODEL_VIEW_MATRIX.set(pageModelView.set(modelView).translate(translateX, translateY, translateZ));
                        shader.MODEL_VIEW_MATRIX.upload();
                    }
                } else if (shader.CHUNK_OFFSET != null) {
                    // Vanilla block shaders derive fog distance from Position + ChunkOffset,
                    // independently of ModelViewMat. Keep both placement and fog camera-relative.
                    shader.CHUNK_OFFSET.set(translateX, translateY, translateZ);
                    shader.CHUNK_OFFSET.upload();
                }
                vbo.draw();
                statDrawCalls++;
            }
        } finally {
            ProjectionShader.end(shader);
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    /**
     * Conservative screen rectangle of the projection AABB (plus a two-block margin for normal
     * block entities). Returns null when any corner is behind the near plane; the caller then
     * falls back to the two-pass path instead of risking a clipped composite.
     */
    /**
     * Conservative screen rectangle of the projection AABB (plus a two-block margin for normal
     * block entities). The view-projection already contains the rotation/mirror animation, so the
     * eight transformed corners bound the animated projection as well. Corners behind the near
     * plane fall back to the full screen; the composite is alpha-masked, so the extra area is
     * harmless.
     */
    static int[] screenRect(AABB box, Vec3 camera,
                            Matrix4f modelView, Matrix4f projection, int width, int height) {
        Matrix4f mvp = new Matrix4f(projection).mul(modelView);
        double margin = 2.0D;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            // The world is rendered camera-relative: translate before the view-projection.
            float x = (float) ((((corner & 1) == 0) ? box.minX - margin : box.maxX + margin) - camera.x);
            float y = (float) ((((corner & 2) == 0) ? box.minY - margin : box.maxY + margin) - camera.y);
            float z = (float) ((((corner & 4) == 0) ? box.minZ - margin : box.maxZ + margin) - camera.z);
            Vector4f point = new Vector4f(x, y, z, 1.0F);
            mvp.transform(point);
            if (!(point.w > 1.0E-4F)) return new int[] {0, 0, width, height};
            float ndcX = point.x / point.w;
            float ndcY = point.y / point.w;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return new int[] {0, 0, width, height};
            double screenX = (ndcX * 0.5D + 0.5D) * width;
            double screenY = (0.5D - ndcY * 0.5D) * height;
            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
        }
        int x0 = (int) Math.max(0.0D, Math.floor(minX) - 4.0D);
        int y0 = (int) Math.max(0.0D, Math.floor(minY) - 4.0D);
        int x1 = (int) Math.min(width, Math.ceil(maxX) + 4.0D);
        int y1 = (int) Math.min(height, Math.ceil(maxY) + 4.0D);
        return x1 <= x0 || y1 <= y0 ? null : new int[] {x0, y0, x1, y1};
    }

    private boolean hasTranslucentLayer() {
        for (RenderType layer : visibleLayers) {
            if (ProjectionRenderType.isTranslucent(layer)) return true;
        }
        return false;
    }

    /**
     * Composite opaque projection first, then draw only the translucent layer over it. The
     * opaque target depth is copied to the main target so glass behind the projection or behind
     * terrain still fails the depth test. When clouds are enabled the depth remains until
     * AFTER_WEATHER; otherwise world depth is restored immediately.
     */
    private void drawTranslucentOverMain(RenderTarget mainTarget, int width, int height, int[] rect,
                                         AABB box, Vec3 camera, Matrix4f modelView,
                                         Matrix4f projectionMatrix, boolean cull,
                                         boolean depthPrepared) {
        if (!depthPrepared) {
            if (!depthTargets.begin(mainTarget)) return;
            statDepthCopies++;
            compositeTarget.copyDepthTo(mainTarget, rect);
            statDepthCopies++;
        }
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (RenderType layer : visibleLayers) {
                if (!ProjectionRenderType.isTranslucent(layer)) continue;
                drawLayer(layer, visibleMeshes, box, camera, modelView, projectionMatrix, false, cull);
            }
        } finally {
            if (!depthPrepared) {
                depthTargets.restoreWorld(mainTarget);
                statDepthCopies++;
            }
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableBlend();
        }
    }

    private void drawLayersOpaque(Iterable<SelectionProjectionMesh> meshes, AABB box, Vec3 camera,
                                  Matrix4f modelView, Matrix4f projectionMatrix, boolean cull) {
        for (RenderType layer : visibleLayers) {
            // Transparent geometry is submitted after the opaque composite; treating it as a
            // depth-writing cutout here is what made projection glass hide the blocks behind it.
            if (ProjectionRenderType.isTranslucent(layer)) continue;
            drawLayerOpaque(layer, meshes, box, camera, modelView, projectionMatrix, cull);
        }
    }

    private void drawLayerOpaque(RenderType layer, Iterable<SelectionProjectionMesh> meshes,
                                 AABB box, Vec3 camera, Matrix4f modelView, Matrix4f projectionMatrix,
                                 boolean cull) {
        RenderType type = ProjectionRenderType.opaqueOf(layer, cull);
        type.setupRenderState();
        if (cull) {
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glFrontFace(GL11.GL_CCW);
        } else {
            RenderSystem.disableCull();
        }
        ShaderInstance shader = RenderSystem.getShader();
        try {
            ProjectionShader.begin(shader, modelView, projectionMatrix);
            for (SelectionProjectionMesh mesh : meshes) {
                VertexBuffer vbo = mesh.layerVbos().get(layer);
                if (vbo == null) continue;
                var page = mesh.page();
                vbo.bind();
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(
                            cameraRelativeOrigin(box.minX + page.x(), camera.x),
                            cameraRelativeOrigin(box.minY + page.y(), camera.y),
                            cameraRelativeOrigin(box.minZ + page.z(), camera.z));
                    shader.CHUNK_OFFSET.upload();
                }
                vbo.draw();
                statDrawCalls++;
            }
        } finally {
            ProjectionShader.end(shader);
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    /**
     * Opaque block-entity submission, followed by the translucent overlay layers that need to be
     * blended over it (banner patterns, beacon beams, end portals). The target keeps only the
     * nearest opaque surface; overlays then compose in bake order with the target alpha preserved,
     * so the final 0.6 composite still applies exactly once.
     */
    private void renderBlockEntitiesOpaque(java.util.List<SelectionProjectionMesh> projections,
                                           AABB box, Vec3 camera, Matrix4f modelView,
                                           Matrix4f projectionMatrix) {
        collectEntityLayers(projections);
        drawOpaqueEntityLayers(projections, box, camera, modelView, projectionMatrix);
        drawOverlayEntityLayers(projections, box, camera, modelView, projectionMatrix);
    }

    private void collectEntityLayers(java.util.List<SelectionProjectionMesh> projections) {
        entityLayers.clear();
        opaqueEntityLayers.clear();
        overlayEntityLayers.clear();
        for (SelectionProjectionMesh mesh : projections) {
            if (mesh.blockEntities().isEmpty()) continue;
            for (RenderType type : mesh.blockEntityVbos().keySet()) {
                if (!entityLayers.add(type)) continue;
                if (ProjectionBlockEntityLayers.isOverlay(type)) overlayEntityLayers.add(type);
                else opaqueEntityLayers.add(type);
            }
        }
    }

    private void drawOpaqueEntityLayers(java.util.List<SelectionProjectionMesh> projections,
                                        AABB box, Vec3 camera, Matrix4f modelView,
                                        Matrix4f projectionMatrix) {
        for (RenderType type : opaqueEntityLayers) {
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            try {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.disableBlend();
                ProjectionShader.begin(shader, modelView, projectionMatrix);
                drawBlockEntityPages(projections, box, camera, modelView, shader, type);
            } finally {
                ProjectionShader.end(shader);
                VertexBuffer.unbind();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                type.clearRenderState();
            }
        }
    }

    private void drawOverlayEntityLayers(java.util.List<SelectionProjectionMesh> projections,
                                         AABB box, Vec3 camera, Matrix4f modelView,
                                         Matrix4f projectionMatrix) {
        if (overlayEntityLayers.isEmpty()) return;
        for (RenderType type : overlayEntityLayers) {
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            try {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                // Overlay geometry shares the base surface depth; never let it occlude the layers
                // above it in the bake order.
                RenderSystem.depthMask(false);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                if (ProjectionBlockEntityLayers.isAdditive(type)) {
                    GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                } else {
                    // Keep the target alpha written by the opaque base; only compose RGB here.
                    GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ZERO, GL11.GL_ONE);
                }
                ProjectionShader.begin(shader, modelView, projectionMatrix);
                drawBlockEntityPages(projections, box, camera, modelView, shader, type);
            } finally {
                ProjectionShader.end(shader);
                VertexBuffer.unbind();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                type.clearRenderState();
            }
        }
    }

    private void drawBlockEntityPages(java.util.List<SelectionProjectionMesh> projections,
                                      AABB box, Vec3 camera, Matrix4f modelView,
                                      ShaderInstance shader, RenderType type) {
        for (SelectionProjectionMesh mesh : projections) {
            if (mesh.blockEntities().isEmpty()) continue;
            VertexBuffer vbo = mesh.blockEntityVbos().get(type);
            if (vbo == null) continue;
            var page = mesh.page();
            blockEntityModelView.set(modelView).translate(
                    cameraRelativeOrigin(box.minX + page.x(), camera.x),
                    cameraRelativeOrigin(box.minY + page.y(), camera.y),
                    cameraRelativeOrigin(box.minZ + page.z(), camera.z));
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(blockEntityModelView);
                shader.MODEL_VIEW_MATRIX.upload();
            }
            vbo.bind();
            vbo.draw();
            statBlockEntityDraws++;
        }
    }

    private void renderBlockEntities(SelectionOverlayState state,
                                     java.util.List<SelectionProjectionMesh> projections,
                                     AABB box, Vec3 camera, Matrix4f modelView,
                                     Matrix4f projectionMatrix, boolean depthPass) {
        // Cache in spatial pages, submit by material. All entity depth is written before any
        // colour (including terrain), so inter-page/material overlaps blend only once.
        collectEntityLayers(projections);
        orderedEntityLayers.clear();
        orderedEntityLayers.addAll(opaqueEntityLayers);
        orderedEntityLayers.addAll(overlayEntityLayers);
        for (RenderType type : orderedEntityLayers) {
            boolean overlay = ProjectionBlockEntityLayers.isOverlay(type);
            type.setupRenderState();
            net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            ShaderInstance shader = RenderSystem.getShader();
            try {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                if (state.isMirrorAnimating()) RenderSystem.disableCull();
                RenderSystem.colorMask(!depthPass, !depthPass, !depthPass, !depthPass);
                RenderSystem.depthMask(depthPass);
                if (depthPass) {
                    RenderSystem.disableBlend();
                } else {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    // Overlay vertices are baked at alpha 1.0 so the banner base colour/patterns
                    // can cover the white base; the old two-pass path still needs 0.6 here.
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                            overlay ? SelectionBlockProjectionState.PROJECTION_ALPHA : 1.0F);
                }
                // Use the actual material shader in depth too: text, banners and cutout skins
                // must retain their alpha holes. A position-only pass fills those holes.
                ProjectionShader.begin(shader, modelView, projectionMatrix);
                drawBlockEntityPages(projections, box, camera, modelView, shader, type);
            } finally {
                ProjectionShader.end(shader);
                VertexBuffer.unbind();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                type.clearRenderState();
            }
        }
    }
}
