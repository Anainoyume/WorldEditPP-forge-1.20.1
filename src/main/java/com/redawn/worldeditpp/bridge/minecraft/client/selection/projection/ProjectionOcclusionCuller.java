package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Conservative page-level occlusion culling against the world depth buffer.
 *
 * <p>Each candidate page submits its camera-relative bounding box as a depth-only draw inside an
 * {@code GL_ANY_SAMPLES_PASSED} query. The result is read one frame later; a page is skipped only
 * when the previous query proved that no sample of its box passed the world depth. Queries are
 * re-issued whenever the view matrix or the page bounds change, and occluded pages are refreshed
 * periodically so chunk changes can make them visible again.</p>
 *
 * <p>Limitation: world depth also contains translucent surfaces (water, glass, stained glass).
 * A page fully behind such a surface is treated as occluded even though it may be visible through
 * the tint. This is why this path is a feature branch and not the default rendering.</p>
 */
final class ProjectionOcclusionCuller implements AutoCloseable {
    // AFTER_PARTICLES includes translucent world depth: queries can wrongly hide previews
    // behind glass/water. They also cost a draw per visible page. Keep the experiment opt-in;
    // ordinary depth testing still provides world occlusion without query readback/submission.
    static boolean ENABLED = false;
    private static final int REFRESH_FRAMES = 20;

    private final Map<Long, PageQuery> queries = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Matrix4f lastView = new Matrix4f();
    private Vec3 lastCamera = Vec3.ZERO;
    private Matrix4f modelView = new Matrix4f();
    private Matrix4f projection = new Matrix4f();
    private boolean hasView;
    private int viewVersion;
    private int frame;

    void beginFrame(Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        this.modelView = modelView;
        this.projection = projection;
        boolean changed = !hasView || !lastView.equals(modelView) || !lastCamera.equals(camera);
        if (changed) {
            viewVersion++;
            lastView.set(modelView);
            lastCamera = camera;
            hasView = true;
        }
        pending.clear();
    }

    /** Called during page collection; returns false only with a fresh occluded result. */
    boolean isVisible(long key, float x0, float y0, float z0, float x1, float y1, float z1) {
        if (!ENABLED) return true;
        PageQuery query = queries.get(key);
        if (query == null) {
            query = new PageQuery(GL15.glGenQueries());
            queries.put(key, query);
        }
        if (query.id == 0) return true; // query allocation failed; never cull blindly
        if (!query.sameBounds(x0, y0, z0, x1, y1, z1)) {
            // Bounds changed (stretch/resize): any in-flight or stored result is unrelated.
            query.setBounds(x0, y0, z0, x1, y1, z1);
            query.visible = true;
            query.issuedVersion = -1;
        }
        poll(query);
        boolean current = query.issuedVersion == viewVersion && query.visible == false;
        if (current) {
            if (frame - query.lastQueryFrame >= REFRESH_FRAMES) pending.add(new Pending(query, x0, y0, z0, x1, y1, z1));
            return false;
        }
        if (!query.pending) pending.add(new Pending(query, x0, y0, z0, x1, y1, z1));
        return true;
    }

    void endFrame() {
        if (!ENABLED) {
            frame++;
            return;
        }
        for (Pending request : pending) {
            if (request.query.pending) continue;
            drawQuery(request);
        }
        frame++;
    }

    private void poll(PageQuery query) {
        if (!query.pending) return;
        if (GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
        int result = GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT);
        query.pending = false;
        query.visible = result != 0;
    }

    private void drawQuery(Pending request) {
        PageQuery query = request.query;
        GL15.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, query.id);
        try {
            drawBox(request);
        } finally {
            GL15.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
        }
        query.pending = true;
        query.lastQueryFrame = frame;
        // A result only describes the view it was issued in; a change invalidates it.
        query.issuedVersion = viewVersion;
    }

    private void drawBox(Pending box) {
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
        var stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        stack.mulPoseMatrix(modelView);
        RenderSystem.applyModelViewMatrix();
        try {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            // Six faces; winding does not matter because culling is disabled for the query box.
            quad(buffer, box.x0, box.y0, box.z0, box.x1, box.y0, box.z0, box.x1, box.y0, box.z1, box.x0, box.y0, box.z1);
            quad(buffer, box.x0, box.y1, box.z1, box.x1, box.y1, box.z1, box.x1, box.y1, box.z0, box.x0, box.y1, box.z0);
            quad(buffer, box.x0, box.y0, box.z1, box.x1, box.y0, box.z1, box.x1, box.y1, box.z1, box.x0, box.y1, box.z1);
            quad(buffer, box.x1, box.y0, box.z0, box.x0, box.y0, box.z0, box.x0, box.y1, box.z0, box.x1, box.y1, box.z0);
            quad(buffer, box.x0, box.y0, box.z0, box.x0, box.y0, box.z1, box.x0, box.y1, box.z1, box.x0, box.y1, box.z0);
            quad(buffer, box.x1, box.y0, box.z1, box.x1, box.y0, box.z0, box.x1, box.y1, box.z0, box.x1, box.y1, box.z1);
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
        }
    }

    private static void quad(BufferBuilder buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        buffer.vertex(x0, y0, z0).endVertex();
        buffer.vertex(x1, y1, z1).endVertex();
        buffer.vertex(x2, y2, z2).endVertex();
        buffer.vertex(x3, y3, z3).endVertex();
    }

    @Override
    public void close() {
        for (PageQuery query : queries.values()) {
            if (query.id != 0) GL15.glDeleteQueries(query.id);
        }
        queries.clear();
        pending.clear();
    }

    private static final class PageQuery {
        final int id;
        boolean pending;
        boolean visible = true;
        int issuedVersion = -1;
        int lastQueryFrame = -REFRESH_FRAMES;
        float x0, y0, z0, x1, y1, z1;

        PageQuery(int id) {
            this.id = id;
        }

        boolean sameBounds(float ax0, float ay0, float az0, float ax1, float ay1, float az1) {
            return x0 == ax0 && y0 == ay0 && z0 == az0 && x1 == ax1 && y1 == ay1 && z1 == az1;
        }

        void setBounds(float ax0, float ay0, float az0, float ax1, float ay1, float az1) {
            x0 = ax0; y0 = ay0; z0 = az0; x1 = ax1; y1 = ay1; z1 = az1;
        }
    }

    private record Pending(PageQuery query, float x0, float y0, float z0, float x1, float y1, float z1) {}
}
