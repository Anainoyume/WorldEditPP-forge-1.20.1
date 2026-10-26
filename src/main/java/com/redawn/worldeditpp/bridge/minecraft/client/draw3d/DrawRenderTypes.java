package com.redawn.worldeditpp.bridge.minecraft.client.draw3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class DrawRenderTypes extends RenderStateShard {
    private static final RenderStateShard.DepthTestStateShard ALWAYS_DEPTH = new RenderStateShard.DepthTestStateShard("worldeditpp_draw_always", 519) {
        @Override
        public void setupRenderState() { RenderSystem.disableDepthTest(); }
        @Override
        public void clearRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }
    };

    private static final RenderType DRAW_OCCLUDED = RenderType.create(
            "worldeditpp_draw_occluded",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(ALWAYS_DEPTH)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    private static final RenderType DRAW_VISIBLE = RenderType.create(
            "worldeditpp_draw_visible",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    public static RenderType drawOccluded() { return DRAW_OCCLUDED; }
    public static RenderType drawVisible() { return DRAW_VISIBLE; }

    private DrawRenderTypes() { super("worldeditpp_draw", () -> {}, () -> {}); }
}
