package dev.worldeditpp.testing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayContext;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Real mixed-building rendering: exterior/interior views plus hidden-container pixel equivalence. */
final class BuildingProjectionProbe {
    private static final double[][] VIEWS = {{6, 0, -12}, {130, 75, -65}, {6, 2, 7}};
    private static int view, ticks;
    private static boolean requested, captured;
    private static final List<String> failures = new ArrayList<>();
    private static final List<Long> frames = new ArrayList<>();

    static boolean tick(Minecraft mc, BlockPos target) throws Exception {
        if (captured) { view++; ticks = 0; captured = false; }
        if (view == VIEWS.length) return true;
        if (ticks++ == 0) {
            overlay().nudgeLocalTo(target.getX(), target.getY(), target.getZ(),
                    target.getX() + 479, target.getY() + 63, target.getZ() + 63);
            double[] p = VIEWS[view];
            mc.player.moveTo(target.getX() + p[0], target.getY() + p[1], target.getZ() + p[2],
                    view == 1 ? 30 : 0, view == 1 ? 30 : 0);
        }
        if (ticks >= 25) requested = true;
        return false;
    }

    static void render(RenderLevelStageEvent event) throws Exception {
        if (!requested) return;
        requested = false;
        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        Path dir = Path.of(System.getProperty("worldeditpp.test.report")).getParent();
        var context = new SelectionOverlayContext(mc, event.getCamera(), event.getPoseStack(), event.getProjectionMatrix());
        var state = overlay();
        SelectionBlockProjectionLayer layer = new SelectionBlockProjectionLayer();
        try {
            clear();
            long start = System.nanoTime();
            layer.render(context, state);
            org.lwjgl.opengl.GL11.glFinish();
            frames.add(System.nanoTime() - start);
            try (var full = Screenshot.takeScreenshot(target)) {
                full.writeToFile(dir.resolve("building-projection-" + view + ".png"));
                if (view == 0 || view == 2) {
                    Object pages = field(SelectionBlockProjectionController.state(), "mesh");
                    List<List<Object>> lists = new ArrayList<>();
                    List<List<Object>> saved = new ArrayList<>();
                    try {
                        for (Object mesh : ((Map<?, ?>) field(pages, "pages")).values()) {
                            @SuppressWarnings("unchecked") List<Object> entities = (List<Object>) field(mesh, "blockEntities");
                            lists.add(entities); saved.add(new ArrayList<>(entities)); entities.clear();
                        }
                        clear(); layer.render(context, state);
                        try (var hidden = Screenshot.takeScreenshot(target)) {
                            hidden.writeToFile(dir.resolve("building-without-entities-" + view + ".png"));
                            int changed = 0, colored = 0;
                            // Centre of the front brick wall: room containers are behind it.
                            int minX = view == 0 ? full.getWidth() / 2 - 180 : 0;
                            int maxX = view == 0 ? full.getWidth() / 2 + 150 : full.getWidth();
                            int minY = view == 0 ? full.getHeight() / 2 - 12 : 0;
                            int maxY = view == 0 ? full.getHeight() / 2 + 12 : full.getHeight();
                            for (int y = minY; y < maxY; y++)
                                for (int x = minX; x < maxX; x++) {
                                    int a = full.getPixelRGBA(x, y), b = hidden.getPixelRGBA(x, y);
                                    if ((a & 0xffffff) != 0) colored++;
                                    if (a != b) changed++;
                                }
                            if (view == 0 && (colored < 7000 || changed != 0))
                                failures.add("Building front self-occlusion failed: colored=" + colored + " changed=" + changed);
                            if (view == 2 && changed < 20)
                                failures.add("Interior block entities did not contribute visible pixels: " + changed);
                            Files.writeString(dir.resolve("building-occlusion-" + view + ".txt"),
                                    "Colored pixels: " + colored + "; entity differences: " + changed + "\n");
                            if (view == 0) {
                                clear(1); layer.render(context, state);
                                try (var white = Screenshot.takeScreenshot(target)) {
                                    white.writeToFile(dir.resolve("building-white-background.png"));
                                    // A single nearest projected surface must retain its 0.6 alpha.
                                    // Compare two backgrounds, independent of texture/lighting colour.
                                    int expected = Math.round(255 * (1 - SelectionBlockProjectionState.PROJECTION_ALPHA));
                                    int wrong = 0;
                                    for (int y = minY; y < maxY; y++) for (int x = minX; x < maxX; x++) {
                                        int blackPixel = hidden.getPixelRGBA(x, y), whitePixel = white.getPixelRGBA(x, y);
                                        for (int shift = 0; shift < 24; shift += 8) {
                                            int delta = ((whitePixel >>> shift) & 255) - ((blackPixel >>> shift) & 255);
                                            if (Math.abs(delta - expected) > 3) { wrong++; break; }
                                        }
                                    }
                                    if (wrong != 0) failures.add("Projection alpha mismatch: " + wrong + " wall pixels");
                                    Files.writeString(dir.resolve("building-alpha.txt"),
                                            "Expected background contribution=" + expected + "; mismatched wall pixels=" + wrong + "\n");
                                }
                            }
                        }
                    } finally { for (int i = 0; i < lists.size(); i++) lists.get(i).addAll(saved.get(i)); }
                }
            }
            Files.writeString(dir.resolve("building-render-samples.txt"),
                    "Three diagnostic CPU+GPU synchronized draw samples (ns), not steady-state FPS: " + frames + "\n");
        } finally { layer.close(); }
        captured = true;
    }

    static void verify() {
        if (view != VIEWS.length) throw new AssertionError("Building render views incomplete");
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    private static void clear() {
        clear(0);
    }
    private static void clear(float background) {
        RenderSystem.colorMask(true, true, true, true); RenderSystem.depthMask(true);
        RenderSystem.clearColor(background, background, background, 1); RenderSystem.clearDepth(1);
        RenderSystem.clear(0x4000 | 0x0100, false);
    }
    private static Object field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static SelectionOverlayState overlay() throws Exception {
        var m = SelectionClientController.class.getDeclaredMethod("state"); m.setAccessible(true);
        return (SelectionOverlayState) m.invoke(null);
    }
}
