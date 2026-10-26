package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class SelectionOverlayRenderTypes extends RenderStateShard {
    private static final ResourceLocation ROTATION_HANDLE_TEXTURE = new ResourceLocation(
            WorldEditPlusPlus.MOD_ID, "textures/gui/rotate_arrows.png");
    private static final ResourceLocation FORCEFIELD_TEXTURE = new ResourceLocation(
            "textures/misc/forcefield.png");
    private static final ShaderStateShard POSITION_TEX_COLOR_SHADER = new ShaderStateShard(
            GameRenderer::getPositionTexColorShader);
    private static final RenderStateShard.DepthTestStateShard SELECTION_NO_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("worldeditpp_always", 519) {
        @Override
        public void setupRenderState() {
            RenderSystem.disableDepthTest();
        }

        @Override
        public void clearRenderState() {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        }
    };
    private static final TransparencyStateShard FORCEFIELD_TRANSPARENCY = new TransparencyStateShard(
            "worldeditpp_forcefield_additive",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderType SELECTION_OCCLUDED = RenderType.create(
            "worldeditpp_selection_occluded",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(SELECTION_NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    private static final RenderType SELECTION_VISIBLE = RenderType.create(
            "worldeditpp_selection_visible",
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
    // POSITION_TEX_COLOR does not consume normals, so every face keeps the same tint.
    private static final RenderType ROTATION_HANDLE = RenderType.create(
            "worldeditpp_rotation_handle",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR_SHADER)
                    .setTextureState(new TextureStateShard(ROTATION_HANDLE_TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    private static final RenderType MIRROR_FORCEFIELD_OCCLUDED = createForcefield(
            "worldeditpp_mirror_forcefield_occluded", SELECTION_NO_DEPTH_TEST);
    private static final RenderType MIRROR_FORCEFIELD_VISIBLE = createForcefield(
            "worldeditpp_mirror_forcefield_visible", LEQUAL_DEPTH_TEST);

    private static RenderType createForcefield(String name, DepthTestStateShard depthTest) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_TEX_COLOR_SHADER)
                        .setTextureState(new TextureStateShard(FORCEFIELD_TEXTURE, false, false))
                        .setTransparencyState(FORCEFIELD_TRANSPARENCY)
                        .setDepthTestState(depthTest)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));
    }

    public static RenderType selectionOccluded() {
        return SELECTION_OCCLUDED;
    }

    public static RenderType selectionVisible() {
        return SELECTION_VISIBLE;
    }

    public static RenderType rotationHandle() {
        return ROTATION_HANDLE;
    }

    public static RenderType mirrorForcefieldOccluded() {
        return MIRROR_FORCEFIELD_OCCLUDED;
    }

    public static RenderType mirrorForcefieldVisible() {
        return MIRROR_FORCEFIELD_VISIBLE;
    }

    private SelectionOverlayRenderTypes() {
        super("worldeditpp_selection_overlay", () -> {
        }, () -> {
        });
    }
}
