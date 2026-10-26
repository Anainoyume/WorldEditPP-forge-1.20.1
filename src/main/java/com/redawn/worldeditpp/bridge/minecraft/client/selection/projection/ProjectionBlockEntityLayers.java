package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import net.minecraft.client.renderer.RenderType;

/**
 * Classifies block-entity material layers for the opaque projection composite.
 *
 * <p>Opaque layers write depth and form the single nearest surface. Overlay layers (banner
 * patterns, beacon beams, end portals, ...) must be composited afterwards with blending and
 * without depth writes; otherwise their transparent texels overwrite the base surface.</p>
 */
final class ProjectionBlockEntityLayers {
    static boolean isOverlay(RenderType type) {
        String name = name(type);
        return name.equals("entity_no_outline")
                || name.equals("entity_shadow")
                || name.equals("beacon_beam")
                || name.equals("eyes")
                || name.equals("entity_alpha")
                || name.equals("item_entity_translucent_cull")
                || name.startsWith("entity_translucent");
    }

    /** Additive overlays keep their glow but must not change the target alpha. */
    static boolean isAdditive(RenderType type) {
        String name = name(type);
        return name.equals("beacon_beam") || name.equals("eyes");
    }

    /**
     * CompositeRenderType overrides toString() as {@code RenderType[name:state]}; the plain name
     * is the stable layer identity used by the projection classification.
     */
    static String name(RenderType type) {
        String text = type.toString();
        if (text.startsWith("RenderType[")) {
            int end = text.indexOf(':', 11);
            if (end > 11) return text.substring(11, end);
        }
        return text;
    }

    private ProjectionBlockEntityLayers() { }
}
