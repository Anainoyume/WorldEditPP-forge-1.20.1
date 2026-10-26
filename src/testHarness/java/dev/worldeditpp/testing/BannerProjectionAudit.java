package dev.worldeditpp.testing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayContext;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/**
 * Real-client regression for banner pattern layers in the opaque projection composite.
 *
 * <p>Four banners are injected through the production snapshot path on a cleared target: a plain
 * white cloth (BASE only), a white cloth with a red border, a red cloth (BASE only), and a white
 * cloth with a blue creeper. The old opaque path treated every banner layer as opaque and turned
 * the cloth into a near-white/overwritten surface; the probe requires the white/red dot colours
 * and the blue pattern to be visible at the same time.</p>
 */
final class BannerProjectionAudit {
    private static final SelectionOverlayState overlay = new SelectionOverlayState();
    private static final int ORIGIN_X = 1000;
    private static final int ORIGIN_Y = 80;
    private static final int ORIGIN_Z = 1000;
    private static final int SIZE_X = 7;
    private static final int SIZE_Y = 6;
    private static final int SIZE_Z = 7;
    private static SelectionBlockProjectionLayer layer;
    private static int phase, ticks, frames;
    private static Path dir;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 3_000) throw new AssertionError("Banner projection audit timed out: phase=" + phase);
        if (phase == 0) {
            dir = Path.of(System.getProperty("worldeditpp.test.report")).getParent().resolve("bannerAudit");
            Files.createDirectories(dir);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.options.renderDistance().set(6);
            mc.options.fov().set(70);
            mc.options.hideGui = true;
            var server = mc.getSingleplayerServer();
            if (server == null) throw new AssertionError("Integrated server is missing");
            server.submit(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) {
                    player.teleportTo(server.overworld(), 1003.5D, 83.0D, 1008.5D, 180.0F, 8.0F);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
            }).get();
            phase = 1;
        } else if (phase == 1) {
            var state = SelectionBlockProjectionController.state();
            state.reset();
            state.setActive(true);
            var apply = state.getClass().getDeclaredMethod(
                    "applySnapshot", SelectionProjectionAssembler.SnapshotData.class);
            apply.setAccessible(true);
            if (!(Boolean) apply.invoke(state, snapshot())) throw new AssertionError("Banner snapshot rejected");
            overlay.replaceCuboidSelection(new CuboidSelectionPayload(true,
                    ORIGIN_X, ORIGIN_Y, ORIGIN_Z,
                    ORIGIN_X + SIZE_X - 1, ORIGIN_Y + SIZE_Y - 1, ORIGIN_Z + SIZE_Z - 1,
                    ORIGIN_X, ORIGIN_Y, ORIGIN_Z,
                    ORIGIN_X + SIZE_X - 1, ORIGIN_Y + SIZE_Y - 1, ORIGIN_Z + SIZE_Z - 1));
            layer = new SelectionBlockProjectionLayer();
            phase = 2;
        } else if (phase == 2) {
            var state = SelectionBlockProjectionController.state();
            if (!state.isReady()) return false;
            frames = 0;
            phase = 3;
        } else if (phase == 3) {
            if (++frames < 30) return false;
            verify(mc);
            var state = SelectionBlockProjectionController.state();
            state.reset();
            layer.close();
            layer = null;
            return true;
        }
        return false;
    }

    static void render(RenderLevelStageEvent event) {
        if (phase != 3 || layer == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, false);
        layer.render(new SelectionOverlayContext(mc, event.getCamera(), event.getPoseStack(),
                event.getProjectionMatrix()), overlay);
    }

    private static SelectionProjectionAssembler.SnapshotData snapshot() {
        Block[] paletteBlocks = {Blocks.STONE, Blocks.WHITE_BANNER, Blocks.RED_BANNER};
        int[] palette = new int[paletteBlocks.length];
        for (int i = 0; i < paletteBlocks.length; i++) {
            palette[i] = Block.getId(paletteBlocks[i].defaultBlockState());
        }
        List<Integer> entries = new ArrayList<>();
        List<SelectionBlockEntityData> nbt = new ArrayList<>();
        for (int z = 0; z < SIZE_Z; z++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int x = 0; x < SIZE_X; x++) {
                    int paletteIndex = -1;
                    if (y == 0) paletteIndex = 0;
                    if (x == 0 && y == 1 && z == 0) paletteIndex = 1;
                    if (x == 2 && y == 1 && z == 0) paletteIndex = 1;
                    if (x == 4 && y == 1 && z == 0) paletteIndex = 2;
                    if (x == 6 && y == 1 && z == 0) paletteIndex = 1;
                    if (paletteIndex < 0) continue;
                    int cell = x + SIZE_X * (y + SIZE_Y * z);
                    entries.add(cell);
                    entries.add(paletteIndex);
                    ListTag patterns;
                    if (paletteIndex == 1 && x == 0) {
                        patterns = new BannerPattern.Builder().toListTag();
                    } else if (paletteIndex == 1 && x == 2) {
                        patterns = new BannerPattern.Builder()
                                .addPattern(BannerPatterns.BORDER, DyeColor.RED)
                                .addPattern(BannerPatterns.CREEPER, DyeColor.BLUE).toListTag();
                    } else if (paletteIndex == 2) {
                        patterns = new BannerPattern.Builder().toListTag();
                    } else {
                        patterns = new BannerPattern.Builder()
                                .addPattern(BannerPatterns.CREEPER, DyeColor.BLUE).toListTag();
                    }
                    nbt.add(ProjectionBlockEntityNbt.capture(cell, bannerTag(
                            ORIGIN_X + x, ORIGIN_Y + y, ORIGIN_Z + z, patterns)));
                }
            }
        }
        int[] entryArray = entries.stream().mapToInt(Integer::intValue).toArray();
        return new SelectionProjectionAssembler.SnapshotData(77, ORIGIN_X, ORIGIN_Y, ORIGIN_Z,
                SIZE_X, SIZE_Y, SIZE_Z, palette, entryArray, nbt);
    }

    private static CompoundTag bannerTag(int x, int y, int z, ListTag patterns) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:banner");
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.put("Patterns", patterns);
        return tag;
    }

    private static void verify(Minecraft mc) throws Exception {
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(dir.resolve("banner-audit.png"));
            long nonBlack = 0, white = 0, red = 0, blue = 0, colored = 0;
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int argb = pixels.getPixelRGBA(x, y);
                    // NativeImage stores ABGR.
                    int r = argb & 255, g = (argb >> 8) & 255, b = (argb >> 16) & 255;
                    if (r + g + b < 15) continue;
                    nonBlack++;
                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    if (max >= 90 && max - min <= 25) white++;
                    if (r > g + 25 && r > b + 25) red++;
                    if (b > r + 20 && b > g + 10) blue++;
                    if (max - min > 30) colored++;
                }
            }
            String report = String.format(Locale.ROOT,
                    "nonBlack=%d white=%d red=%d blue=%d colored=%d whiteFraction=%.3f%n",
                    nonBlack, white, red, blue, colored,
                    nonBlack == 0 ? 1.0 : (double) white / nonBlack);
            Files.writeString(dir.resolve("banner-audit.txt"), report);
            if (nonBlack < 2000) throw new AssertionError("Banner projection not visible: " + report);
            if (red < 60) throw new AssertionError("Red pattern/base missing: " + report);
            if (blue < 40) throw new AssertionError("Blue pattern missing: " + report);
            if (colored < 150) throw new AssertionError("Banner patterns are not rendered: " + report);
            if (white * 100 >= nonBlack * 80) throw new AssertionError("Banner is nearly white: " + report);
        }
    }

    private BannerProjectionAudit() { }
}
