package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;

/**
 * 委托 VertexConsumer：把写入的顶点 alpha 统一缩放（用于方块实体渲染的整体透明度）。
 * 其余字段原样透传。
 */
public final class AlphaScalingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float alphaScale;

    public AlphaScalingVertexConsumer(VertexConsumer delegate, float alphaScale) {
        this.delegate = delegate;
        this.alphaScale = alphaScale;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, Math.min(255, (int) (alpha * alphaScale)));
        return this;
    }

    @Override
    public VertexConsumer color(int color) {
        return color(
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color),
                FastColor.ARGB32.alpha(color)
        );
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
    @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, Math.min(255, (int) (a * alphaScale))); }
    @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}
