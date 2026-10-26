package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.item.DrawMaterialItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/*
 * 绘制模式指示器。
 *
 * 警告：绘制模式不是子模式（EditSubMode），它是一个瞬态叠加层。
 * 按住 R 键时激活（通过 PREPARE_DRAW.isDown() 检测），松开即失效。
 * 没有状态持久化、没有 NBT 存储、不可通过 Alt+1/2 切换。
 * 在 EditModeIndicatorRenderer 注册表中排在 EditSubMode 之前，
 * 通过 isActive() 优先级覆盖 GUI 显示，但不会修改 EditSubModeState。
 *
 * 注意：坐标轴 hover/holded 时 suppressDrawMode 阻止绘制模式逻辑，
 * isActive() 同步检查 gizmo 状态，避免 GUI 指示器在该场景下显示。
 */
final class DrawIndicator implements EditModeIndicator {
    private static final ResourceLocation ICON = new ResourceLocation("worldeditpp", "textures/gui/draw_mode.png");

    @Override
    public ResourceLocation icon() { return ICON; }
    @Override
    public Component label() {
        var player = Minecraft.getInstance().player;
        boolean replacing = player != null && DrawMaterialItems.block(player.getOffhandItem()).isPresent();
        return Component.translatable(replacing ? "gui.worldeditpp.draw.replace" : "gui.worldeditpp.draw.mode",
                DrawModeController.current().id());
    }
    @Override
    public boolean isActive() {
        return DrawModeController.isDrawing()
                && !SelectionClientController.hasClipboardAdjustment()
                && !SelectionClientController.gizmoManager().isHovered()
                && !SelectionClientController.gizmoManager().isHolded();
    }
}
