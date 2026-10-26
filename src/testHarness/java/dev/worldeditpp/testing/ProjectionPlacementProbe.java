package dev.worldeditpp.testing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayContext;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Real production VBO/shader drawing, with terrain removed so it cannot fake a passing image. */
final class ProjectionPlacementProbe {
    private static final double[][] VIEWS = Boolean.getBoolean("worldeditpp.test.cloudProbe")
            ? new double[][]{{-4, 3, -24}, {24, 4, -4}, {4, 3, 20}}
            : new double[][]{{-4, 3, -7}, {7, 4, -3}, {1, 3, 8}};
    private static int view = -1, settle;
    private static boolean requested, captured;

    static boolean tick(Minecraft mc, BlockPos target) throws Exception {
        if (view == -1) {
            state().nudgeLocalTo(target.getX(), target.getY(), target.getZ(),
                    target.getX() + 2, target.getY() + 1, target.getZ() + 1);
            moveCamera(mc, target, 0);
        }
        if (captured) {
            captured = false;
            if (view == VIEWS.length - 1) return true;
            moveCamera(mc, target, view + 1);
        }
        if (++settle >= 30) requested = true;
        return false;
    }

    private static void moveCamera(Minecraft mc, BlockPos target, int index) {
        view = index;
        settle = 0;
        double[] offset = VIEWS[index];
        double x = target.getX() + offset[0], y = target.getY() + offset[1], z = target.getZ() + offset[2];
        double dx = target.getX() + 1.5 - x, dz = target.getZ() + 1 - z;
        double dy = target.getY() + 1 - (y + mc.player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.moveTo(x, y, z, yaw, pitch);
    }

    static void render(RenderLevelStageEvent event) throws Exception {
        if (!requested) return;
        requested = false;
        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        var state = state();
        var box = state.cuboidSelectionBox(event.getCamera().getPosition());
        Matrix4f expected = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX, box.maxX})
            for (double y : new double[]{box.minY, box.maxY})
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    Vector4f p = expected.transform(new Vector4f((float) x, (float) y, (float) z, 1));
                    require(p.w > 0, "Fixture must remain in front of the camera");
                    float px = (p.x / p.w + 1) * target.width / 2;
                    float py = (1 - p.y / p.w) * target.height / 2;
                    minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                }
        Path report = Path.of(System.getProperty("worldeditpp.test.report"));
        try (var pixels = Screenshot.takeScreenshot(target)) {
            pixels.writeToFile(report.resolveSibling("projection-world-" + view + ".png"));
        }
        // One diagnostic frame: clear all world geometry, then draw only the actual production projection.
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0, 0, 0, 1);
        RenderSystem.clearDepth(1);
        RenderSystem.clear(0x4000 | 0x0100, false);
        SelectionBlockProjectionLayer layer = new SelectionBlockProjectionLayer();
        try {
            layer.render(new SelectionOverlayContext(mc, event.getCamera(), event.getPoseStack(), event.getProjectionMatrix()), state);
            try (var pixels = Screenshot.takeScreenshot(target)) {
                pixels.writeToFile(report.resolveSibling("projection-only-" + view + ".png"));
                int inside = 0, outside = 0;
                for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
                    if ((pixels.getPixelRGBA(x, y) & 0x00FFFFFF) == 0) continue;
                    if (x >= minX - 2 && x <= maxX + 2 && y >= minY - 2 && y <= maxY + 2) inside++;
                    else outside++;
                }
                require(inside > 300, "Projection missing at expected screen bounds, view=" + view + " inside=" + inside);
                require(outside < 10, "Projection drawn outside selection bounds, view=" + view + " outside=" + outside);
                Vector4f sample = expected.transform(new Vector4f((float) (box.minX + 0.5),
                        (float) (box.minY + 0.25), (float) (box.maxZ - 0.5), 1));
                int sx = Math.round((sample.x / sample.w + 1) * target.width / 2);
                int sy = Math.round((1 - sample.y / sample.w) * target.height / 2);
                require((pixels.getPixelRGBA(sx, sy) & 0x00FFFFFF) != 0,
                        "Baked block mesh missing at its expected position (a correct chest alone is insufficient), view=" + view);
            }
        } finally { layer.close(); }
        captured = true;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState state() throws Exception {
        var method = SelectionClientController.class.getDeclaredMethod("state");
        method.setAccessible(true);
        return (com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState) method.invoke(null);
    }
}
