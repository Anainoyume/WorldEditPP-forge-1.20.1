package com.redawn.worldeditpp.bridge.minecraft.client.screen;

import com.redawn.worldeditpp.bridge.minecraft.blockstate.BlockStatePropertiesEditor;
import com.redawn.worldeditpp.bridge.minecraft.blockstate.MinecraftBlockStateView;
import com.redawn.worldeditpp.bridge.api.BlockStateView.PropertyValue;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.GlassPanelRenderer;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.MinecraftUiCanvas;
import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiText;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditResultPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import org.lwjgl.glfw.GLFW;

/** Non-pausing, transparent screen locked to the original crosshair target. */
public final class BlockStateEditorScreen extends Screen {
    private static final double ROW_GAP = 4 * 1.07;
    private static final double ROW_STRIDE = BlockStatePropertyWidget.HEIGHT + ROW_GAP;
    private final BlockPos target;
    private final ResourceLocation dimension;
    private final Block block;
    private final ItemStack titleIcon;
    private final long sessionId = System.nanoTime();
    private final long openedAt = System.nanoTime();
    private final GlassPanelRenderer glass = new GlassPanelRenderer();
    private final List<BlockStatePropertyWidget> rows = new ArrayList<>();
    private BlockState draft;
    private long sentRequest;
    private long receivedRequest;
    private boolean closeKeyReleased;
    private String status = "ready";
    private int panelX, panelY, panelWidth, panelHeight, listTop, listBottom;
    private double scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private BlockStatePropertyWidget menu;
    private int menuX, menuY, menuWidth, menuRows, menuOffset, menuHighlight;
    private float opacity;

    public static boolean canOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.level != null && mc.player != null
                && ClientEditModeController.isEditing()
                && !SelectionClientController.hasClipboardAdjustment()
                && !SelectionClientController.isRotationInProgress()
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && !mc.level.getBlockState(hit.getBlockPos()).isAir();
    }

    public static void openTarget() {
        if (!canOpen()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos().immutable();
        mc.setScreen(new BlockStateEditorScreen(pos, mc.level.dimension().location(), mc.level.getBlockState(pos)));
    }

    private BlockStateEditorScreen(BlockPos target, ResourceLocation dimension, BlockState state) {
        super(Component.translatable("screen.worldeditpp.block_state.title"));
        this.target = target;
        this.dimension = dimension;
        this.block = state.getBlock();
        this.titleIcon = new ItemStack(block);
        this.draft = state;
    }

    @Override protected void init() {
        rows.clear();
        menu = null;
        draggingScrollbar = false;
        for (PropertyValue property : MinecraftBlockStateView.of(draft).properties().stream()
                .sorted(Comparator.<PropertyValue>comparingInt(value -> propertyOrder(block instanceof WallBlock, value.name()))
                        .thenComparing(PropertyValue::name)).toList()) {
            var row = new BlockStatePropertyWidget(property.name(), property.allowedValues(), property.value(),
                    value -> change(property.name(), value), this::openMenu);
            rows.add(addRenderableWidget(row));
        }
        layout();
    }

    private void layout() {
        panelWidth = Math.min(360, Math.max(152, (int) (width * 0.42)));
        panelWidth = Math.min(panelWidth, width - 16);
        panelWidth = Math.round(panelWidth * 0.6F);
        panelX = Math.min(36, Math.max(8, width / 25)) - Math.round((1 - opacity) * 8);
        panelY = Math.min(64, Math.max(8, height / 9));
        panelHeight = Math.min(720, Math.min((int) (height * 0.72), height - panelY - 8));
        listTop = panelY + 52;
        listBottom = panelY + panelHeight - 28;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            row.setX(panelX + 8);
            row.setY(listTop + (int) Math.round(i * ROW_STRIDE - scroll));
            row.setWidth(panelWidth - 16);
            row.opacity = opacity;
            row.visible = row.getY() + row.getHeight() > listTop && row.getY() < listBottom;
        }
        if (menu != null) layoutMenu();
    }

    static int propertyOrder(boolean wall, String property) {
        if (property.equals("waterlogged")) return 2;
        if (wall && property.equals("up")) return 1;
        return 0;
    }

    private double maxScroll() { return Math.max(0, rows.size() * ROW_STRIDE - ROW_GAP - (listBottom - listTop)); }
    private int scrollbarHeight() {
        int track = listBottom - listTop;
        return Math.min(track, Math.max(12, (int) ((double) track * track / (rows.size() * ROW_STRIDE))));
    }
    private int scrollbarTop() {
        return listTop + (int) ((listBottom - listTop - scrollbarHeight()) * scroll / maxScroll());
    }
    private void dragScrollbarTo(double mouseY) {
        scroll = scrollbarScroll(mouseY, listTop, listBottom - listTop, scrollbarHeight(),
                scrollbarGrabOffset, maxScroll());
        layout();
    }

    static double scrollbarScroll(double mouseY, int top, int track, int thumb, double grabOffset, double maximum) {
        int travel = track - thumb;
        return travel <= 0 ? 0 : Math.max(0, Math.min(1, (mouseY - top - grabOffset) / travel)) * maximum;
    }
    private boolean inList(double x, double y) {
        return x >= panelX + 8 && x < panelX + panelWidth - 8 && y >= listTop && y < listBottom;
    }
    private boolean inPanel(double x, double y) {
        return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight;
    }

    @Override public void renderBackground(GuiGraphics graphics) {
        // The world outside the card must remain entirely untouched.
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float progress = Math.min(1, (System.nanoTime() - openedAt) / 120_000_000F);
        opacity = 1 - (1 - progress) * (1 - progress) * (1 - progress);
        layout();
        if (opacity < 0.02F) return;
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, font, width, height);
        glass.render(graphics, panelX, panelY, panelWidth, panelHeight, opacity);
        graphics.pose().pushPose();
        graphics.pose().translate(panelX + 12, panelY + 14, 0);
        graphics.pose().scale(1.37F, 1.37F, 1);
        graphics.renderItem(titleIcon, 0, 0);
        graphics.pose().popPose();
        drawScaledText(graphics, block.getName().getString(), panelX + 40, panelY + 12,
                panelWidth - 68, 1.2F, 0xFFFFFFFF);
        String coordinates = target.getX() + ", " + target.getY() + ", " + target.getZ();
        drawScaledText(graphics, coordinates, panelX + 40, panelY + 28,
                panelWidth - 52, 0.8F, 0xFFAAAAAA);
        graphics.drawString(font, "x", panelX + panelWidth - 22, panelY + 17, ink(0xFFFFFFFF), false);
        graphics.fill(panelX + 8, listTop - 6, panelX + panelWidth - 8, listTop - 5, ink(0xFFFFFFFF));
        graphics.enableScissor(panelX + 8, listTop, panelX + panelWidth - 8, listBottom);
        for (var row : rows) row.render(graphics, mouseX, inList(mouseX, mouseY) ? mouseY : -10000, partialTick);
        if (rows.isEmpty()) graphics.drawWordWrap(font,
                Component.translatable("screen.worldeditpp.block_state.empty"),
                panelX + 18, listTop + 8, panelWidth - 36, textInk(210));
        graphics.disableScissor();
        if (maxScroll() > 0) {
            GlassPanelRenderer.rounded(graphics, panelX + panelWidth - 5, scrollbarTop(), 2,
                    scrollbarHeight(), 1, ink(draggingScrollbar ? 0xEEFFFFFF : 0x88FFFFFF));
        }
        Component footer = sentRequest > receivedRequest
                ? Component.translatable("screen.worldeditpp.block_state.saving")
                : Component.translatable("screen.worldeditpp.block_state." + status,
                        WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get().getTranslatedKeyMessage());
        canvas.textScrolling(UiText.literal(footer.getString()), panelX + 18,
                panelY + panelHeight - 17, panelWidth - 36,
                new UiColor(0xFF888888), false, System.currentTimeMillis());
        if (menu != null) renderMenu(graphics, mouseX, mouseY);
    }

    private int ink(int color) { return GlassPanelRenderer.color(color, opacity); }
    private int textInk(int alpha) { return ink(BlockStatePropertyWidget.textColor(alpha)); }

    private void drawScaledText(GuiGraphics graphics, String text, int x, int y, int availableWidth,
                                float scale, int color) {
        String clipped = font.plainSubstrByWidth(text, Math.max(1, (int) (availableWidth / scale)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, clipped, 0, 0, ink(color), false);
        graphics.pose().popPose();
    }

    private void change(String property, String value) {
        if (!validTarget()) { onClose(); return; }
        draft = BlockStatePropertiesEditor.apply(draft, property, value).orElse(draft);
        PlatformBridge.sendToServer(new BlockStateEditPayload(sessionId, ++sentRequest, dimension, target,
                BuiltInRegistries.BLOCK.getKey(block), property, value));
    }

    public void receive(BlockStateEditResultPayload result) {
        if (result.sessionId() != sessionId || result.requestId() < sentRequest) return;
        receivedRequest = result.requestId();
        if (result.status() == BlockStateEditResultPayload.Status.DENIED
                || result.status() == BlockStateEditResultPayload.Status.UNAVAILABLE) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "screen.worldeditpp.block_state.unavailable"), true);
            onClose();
            return;
        }
        status = result.status() == BlockStateEditResultPayload.Status.APPLIED ? "ready" : "failed";
        BlockState actual = Block.stateById(result.stateId());
        if (actual.is(block)) sync(actual);
    }

    private void sync(BlockState state) {
        draft = state;
        for (var row : rows) {
            row.syncValue(BlockStatePropertiesEditor.value(state,
                    block.getStateDefinition().getProperty(row.property)));
        }
    }

    private boolean validTarget() {
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && ClientEditModeController.isEditing() && minecraft.level.dimension().location().equals(dimension)
                && minecraft.level.hasChunkAt(target) && minecraft.level.getBlockState(target).is(block);
    }

    @Override public void tick() {
        if (!validTarget()) { onClose(); return; }
        if (sentRequest == receivedRequest) sync(minecraft.level.getBlockState(target));
    }

    private void openMenu(BlockStatePropertyWidget row) {
        if (menu == row) { menu = null; return; }
        menu = row;
        menuHighlight = Math.max(0, row.values.indexOf(row.value()));
        menuRows = Math.max(1, Math.min(6, Math.min(row.values.size(), (listBottom - listTop - 8) / 22)));
        menuOffset = Math.max(0, Math.min(row.values.size() - menuRows, menuHighlight - menuRows / 2));
        layoutMenu();
    }

    private void layoutMenu() {
        // Keep the popup beside the controls, including during the card's opening slide.
        menuX = menu.controlX() + menu.controlWidth() + 8;
        menuWidth = Math.max(1, Math.min(width - menuX - 8, Math.max(120, menu.controlWidth())));
        int menuHeight = menuRows * 22 + 8;
        menuY = Math.max(8, Math.min(height - 8 - menuHeight, menu.controlY()));
    }

    private boolean inMenu(double x, double y) {
        return menu != null && x >= menuX && x < menuX + menuWidth
                && y >= menuY && y < menuY + menuRows * 22 + 8;
    }

    private void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.fill(menuX, menuY, menuX + menuWidth, menuY + menuRows * 22 + 8, ink(0xC7000000));
        graphics.renderOutline(menuX, menuY, menuWidth, menuRows * 22 + 8, ink(0xFFFFFFFF));
        for (int row = 0; row < menuRows; row++) {
            int index = menuOffset + row;
            String value = menu.values.get(index);
            int y = menuY + 4 + row * 22;
            boolean hover = mouseX >= menuX && mouseX < menuX + menuWidth && mouseY >= y && mouseY < y + 22;
            if (hover || index == menuHighlight || value.equals(menu.value())) graphics.renderOutline(
                    menuX + 3, y, menuWidth - 6, 22, ink(0xFFFFFFFF));
            graphics.drawString(font, font.plainSubstrByWidth(
                    BlockStatePropertyWidget.label("value", value).getString(), menuWidth - 22),
                    menuX + 13, y + 7, textInk(value.equals(menu.value()) ? 255 : 220), false);
        }
        graphics.pose().popPose();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        if (maxScroll() > 0 && x >= panelX + panelWidth - 7 && x < panelX + panelWidth - 1
                && y >= listTop && y < listBottom) {
            menu = null;
            draggingScrollbar = true;
            int top = scrollbarTop();
            scrollbarGrabOffset = y >= top && y < top + scrollbarHeight() ? y - top : scrollbarHeight() / 2.0;
            dragScrollbarTo(y);
            return true;
        }
        if (menu != null) {
            if (inMenu(x, y)) {
                int index = (int) (y - menuY - 4) / 22 + menuOffset;
                if (y >= menuY + 4 && y < menuY + 4 + menuRows * 22 && index < menu.values.size()) {
                    menu.choose(menu.values.get(index));
                }
            }
            menu = null;
            return true;
        }
        if (x >= panelX + panelWidth - 30 && x < panelX + panelWidth - 8 && y >= panelY + 8 && y < panelY + 32) {
            onClose();
            return true;
        }
        if (inList(x, y)) for (var row : rows) {
            if (row.mouseClicked(x, y, button)) { setFocused(row); return true; }
        }
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar && button == 0) {
            dragScrollbarTo(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        if (draggingScrollbar) return true;
        if (menu != null && inMenu(x, y)) {
            menuOffset = Math.max(0, Math.min(menu.values.size() - menuRows,
                    menuOffset - (int) Math.signum(vertical)));
            return true;
        }
        if (inList(x, y) && menu == null) for (var row : rows) {
            if (row.isMouseOver(x, y)) return row.mouseScrolled(x, y, vertical);
        }
        if (inPanel(x, y)) {
            menu = null;
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * 28));
            layout();
        }
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get().matches(key, scan)) {
            if (closeKeyReleased) onClose();
            return true;
        }
        if (menu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { menu = null; return true; }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
                menuHighlight = Math.floorMod(menuHighlight + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), menu.values.size());
                menuOffset = Math.max(0, Math.min(menu.values.size() - menuRows,
                        Math.max(menuHighlight - menuRows + 1, Math.min(menuOffset, menuHighlight))));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                menu.choose(menu.values.get(menuHighlight));
                menu = null;
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get().matches(key, scan)) {
            closeKeyReleased = true;
            WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get().setDown(false);
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get().setDown(false);
        glass.close();
    }
}
