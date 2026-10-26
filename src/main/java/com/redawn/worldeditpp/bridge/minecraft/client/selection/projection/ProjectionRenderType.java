package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Render types used by the projection depth and translucent color passes. */
public final class ProjectionRenderType extends RenderStateShard {
    private static final Map<RenderType, RenderType> DEPTH = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> BLEND = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_CULL = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> BLEND_CULL = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> OPAQUE = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> OPAQUE_CULL = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_POSITION = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_POSITION_CULL = new IdentityHashMap<>();
    private static final ShaderStateShard POSITION_SHADER = new ShaderStateShard(GameRenderer::getPositionShader);
    private static RenderType solidDepth;
    private static RenderType solidBlend;
    private static RenderType solidDepthCull;
    private static RenderType solidBlendCull;
    private static RenderType solidOpaque;
    private static RenderType solidOpaqueCull;

    static {
        register(RenderType.solid(), RENDERTYPE_SOLID_SHADER, BLOCK_SHEET_MIPPED, "solid", true);
        register(RenderType.cutoutMipped(), RENDERTYPE_CUTOUT_MIPPED_SHADER, BLOCK_SHEET_MIPPED, "cutout_mipped", false);
        register(RenderType.cutout(), RENDERTYPE_CUTOUT_SHADER, BLOCK_SHEET, "cutout", false);
        register(RenderType.translucent(), RENDERTYPE_TRANSLUCENT_SHADER, BLOCK_SHEET_MIPPED, "translucent", true);
        register(RenderType.tripwire(), RENDERTYPE_TRIPWIRE_SHADER, BLOCK_SHEET_MIPPED, "tripwire", false);
        solidDepth = DEPTH.get(RenderType.solid());
        solidBlend = BLEND.get(RenderType.solid());
        solidDepthCull = DEPTH_CULL.get(RenderType.solid());
        solidBlendCull = BLEND_CULL.get(RenderType.solid());
        solidOpaque = OPAQUE.get(RenderType.solid());
        solidOpaqueCull = OPAQUE_CULL.get(RenderType.solid());
    }

    private static void register(RenderType layer, ShaderStateShard shader,
                                 TextureStateShard texture, String name, boolean positionDepth) {
        DEPTH.put(layer, createPass("worldeditpp_projection_depth_" + name, shader, texture,
                NO_TRANSPARENCY, DEPTH_WRITE, LEQUAL_DEPTH_TEST, NO_CULL));
        BLEND.put(layer, createPass("worldeditpp_projection_blend_" + name, shader, texture,
                TRANSLUCENT_TRANSPARENCY, COLOR_WRITE, LEQUAL_DEPTH_TEST, NO_CULL));
        DEPTH_CULL.put(layer, createPass("worldeditpp_projection_depth_cull_" + name, shader, texture,
                NO_TRANSPARENCY, DEPTH_WRITE, LEQUAL_DEPTH_TEST, CULL));
        BLEND_CULL.put(layer, createPass("worldeditpp_projection_blend_cull_" + name, shader, texture,
                TRANSLUCENT_TRANSPARENCY, COLOR_WRITE, LEQUAL_DEPTH_TEST, CULL));
        // Opaque layer pass: colour + depth write, no blending, only the nearest surface survives.
        // Translucent/tripwire content is drawn with cutout alpha discard so texture holes remain.
        ShaderStateShard opaqueShader = shader;
        TextureStateShard opaqueTexture = texture;
        if (layer == RenderType.translucent() || layer == RenderType.tripwire()) {
            opaqueShader = RENDERTYPE_CUTOUT_SHADER;
            opaqueTexture = BLOCK_SHEET;
        }
        OPAQUE.put(layer, createPass("worldeditpp_projection_opaque_" + name, opaqueShader, opaqueTexture,
                NO_TRANSPARENCY, COLOR_DEPTH_WRITE, LEQUAL_DEPTH_TEST, NO_CULL));
        OPAQUE_CULL.put(layer, createPass("worldeditpp_projection_opaque_cull_" + name, opaqueShader, opaqueTexture,
                NO_TRANSPARENCY, COLOR_DEPTH_WRITE, LEQUAL_DEPTH_TEST, CULL));
        if (positionDepth) {
            DEPTH_POSITION.put(layer, createPass("worldeditpp_projection_depth_position_" + name,
                    POSITION_SHADER, BLOCK_SHEET_MIPPED, NO_TRANSPARENCY, DEPTH_WRITE,
                    LEQUAL_DEPTH_TEST, NO_CULL));
            DEPTH_POSITION_CULL.put(layer, createPass("worldeditpp_projection_depth_position_cull_" + name,
                    POSITION_SHADER, BLOCK_SHEET_MIPPED, NO_TRANSPARENCY, DEPTH_WRITE,
                    LEQUAL_DEPTH_TEST, CULL));
        }
    }

    private static RenderType createPass(String name, ShaderStateShard shader, TextureStateShard texture,
                                         TransparencyStateShard transparency, WriteMaskStateShard writeMask,
                                         DepthTestStateShard depthTest, CullStateShard cull) {
        return RenderType.create(
                name,
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(shader)
                        .setTextureState(texture)
                        .setTransparencyState(transparency)
                        .setDepthTestState(depthTest)
                        .setWriteMaskState(writeMask)
                        .setCullState(cull)
                        .setLightmapState(LIGHTMAP)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false)
        );
    }

    public static RenderType depthOf(RenderType layer, boolean cull, boolean positionOnly) {
        if (positionOnly) {
            RenderType position = (cull ? DEPTH_POSITION_CULL : DEPTH_POSITION).get(layer);
            if (position != null) return position;
        }
        return (cull ? DEPTH_CULL : DEPTH).getOrDefault(layer, cull ? solidDepthCull : solidDepth);
    }

    public static boolean hasPositionDepth(RenderType layer) {
        return DEPTH_POSITION.containsKey(layer);
    }

    public static RenderType blendOf(RenderType layer, boolean cull) {
        return (cull ? BLEND_CULL : BLEND).getOrDefault(layer, cull ? solidBlendCull : solidBlend);
    }

    /** Nearest-surface pass used by the opaque composite path: colour + depth write, no blend. */
    public static RenderType opaqueOf(RenderType layer, boolean cull) {
        return (cull ? OPAQUE_CULL : OPAQUE).getOrDefault(layer, cull ? solidOpaqueCull : solidOpaque);
    }

    /** Glass/water/tripwire-like layers must stay out of the nearest-surface opaque pass. */
    public static boolean isTranslucent(RenderType layer) {
        return layer == RenderType.translucent();
    }

    private ProjectionRenderType() {
        super("worldeditpp_projection", () -> {}, () -> {});
    }
}
