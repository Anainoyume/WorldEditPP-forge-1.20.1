package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 委托 VertexConsumer：给每个顶点加上相对于投影快照原点的 section 偏移。
 *
 * LiquidBlockRenderer 以区块 section 相对坐标（pos & 15）发射流体顶点，
 * 原版在绘制时用 CHUNK_OFFSET uniform 补偿；烘焙 VBO 无该 uniform，
 * 因此在烘焙时补上 section 原点并减去快照原点，避免把绝对世界坐标写入 float VBO。
 */
public final class SectionOriginVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float originX;
    private final float originY;
    private final float originZ;

    public SectionOriginVertexConsumer(VertexConsumer delegate, int originX, int originY, int originZ) {
        this.delegate = delegate;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x + originX, y + originY, z + originZ);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int overlay) {
        delegate.overlayCoords(overlay);
        return this;
    }

    @Override
    public VertexConsumer uv2(int light) {
        delegate.uv2(light);
        return this;
    }

    @Override
    public VertexConsumer normal(float normalX, float normalY, float normalZ) {
        delegate.normal(normalX, normalY, normalZ);
        return this;
    }
    @Override public void endVertex() { delegate.endVertex(); }
    @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
    @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}
