package com.redawn.worldeditpp.bridge.forge.client;

import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.GlassPanelRenderer;

import com.redawn.worldeditpp.bridge.minecraft.client.ui.ProjectionResourceReloadListener;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.network.ClientPayloadHandlers;
import com.redawn.worldeditpp.bridge.minecraft.client.item.BlockItemStateMarker;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.AccessibilityClientState;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditPlusPlusNetwork;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.DrawModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.screen.radial.RadialMenuScreen;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.EditModeIndicatorRenderer;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.EditModeCoordinatesRenderer;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayRenderer;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.mode.EditSubMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

@EventBusSubscriber(modid = WorldEditPlusPlus.MOD_ID, value = Dist.CLIENT)
public final class ForgeClientEntrypoint
{

    public static void initialize() {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ForgeConfigurationScreen(parent)));
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(ForgeClientEntrypoint::onClientSetup);
        bus.addListener(ForgeClientEntrypoint::registerKeyMappings);
        ClientPlatformBridge.install(new ForgeClientPlatform());
        WorldEditPlusPlusNetwork.installClientHandler(new ClientPayloadHandlers());
        bus.addListener(ForgeClientEntrypoint::onRegisterGuiLayers);
        bus.addListener(ForgeClientEntrypoint::registerItemDecorations);
        bus.addListener(ForgeClientEntrypoint::registerShaders);
        bus.addListener(ForgeClientEntrypoint::registerClientReloadListeners);
    }

    private static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ProjectionResourceReloadListener());
    }

    private static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(net.minecraft.world.item.BlockItem.class::isInstance)
                .forEach(item -> event.register(item, BlockItemStateMarker::render));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        com.redawn.worldeditpp.bridge.minecraft.client.item.BlockStateItemTooltip.appendStoredState(
                event.getItemStack(), event.getToolTip());
    }

    public static void registerShaders(RegisterShadersEvent event) {
        GlassPanelRenderer.installShader(null);
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation("worldeditpp", "glass_panel"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), GlassPanelRenderer::installShader);
        } catch (IOException exception) {
            com.redawn.worldeditpp.WorldEditPlusPlus.LOGGER.warn("Could not load panel blur shader", exception);
        }
    }

    private static void onRegisterGuiLayers(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(
                "coordinates",
                (gui, graphics, partialTick, width, height) -> EditModeCoordinatesRenderer.render(graphics, partialTick)
        );
        event.registerAboveAll(
                "mode_indicator",
                (gui, graphics, partialTick, width, height) -> EditModeIndicatorRenderer.render(graphics, partialTick)
        );
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        WorldEditPlusPlusKeyMappings.registerAll();
        ClientCompatibility.logEnvironment();
        WorldEditPlusPlus.LOGGER.debug("{} 客户端初始化入口已触发。", WorldEditPlusPlus.MOD_NAME);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(WorldEditPlusPlusKeyMappings.BLOCK_STATE_EDITOR.get());
        // 注册客户端热键。
        event.register(WorldEditPlusPlusKeyMappings.CHANGE_MODE.get());
        event.register(WorldEditPlusPlusKeyMappings.ACCESSIBILITY.get());
        event.register(WorldEditPlusPlusKeyMappings.DELETE_SELECTION.get());
        event.register(WorldEditPlusPlusKeyMappings.SUB_MODE_MOVE.get());
        event.register(WorldEditPlusPlusKeyMappings.SUB_MODE_STRETCH.get());
        event.register(WorldEditPlusPlusKeyMappings.SUB_MODE_ROTATE.get());
        event.register(WorldEditPlusPlusKeyMappings.PREPARE_DRAW.get());
        event.register(WorldEditPlusPlusKeyMappings.ADJUST_CLIPBOARD.get());
        event.register(WorldEditPlusPlusKeyMappings.PASTE_CLIPBOARD.get());
        event.register(WorldEditPlusPlusKeyMappings.DRAW_WHEEL.get());
        event.register(WorldEditPlusPlusKeyMappings.MIRROR_ADJUSTMENT.get());
        event.register(WorldEditPlusPlusKeyMappings.UNDO.get());
        event.register(WorldEditPlusPlusKeyMappings.REDO.get());
    }

    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ClientEditModeController.preTick();
        DrawModeController.tick();
    }

    @SubscribeEvent
    public static void onClientTickPost(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SelectionBlockProjectionController.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        resetConnectionState(null);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetConnectionState(event.getPlayer());
    }

    private static void resetConnectionState(net.minecraft.client.player.LocalPlayer player) {
        ClientEditModeController.resetConnectionState(player);
        AccessibilityClientState.reset();
        EditSubModeState.reset();
        DrawModeController.reset();
        SelectionClientController.resetConnectionState();
        SelectionBlockProjectionController.resetConnectionState();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        // 编辑模式中的组合键滚轮由当前工具或子模式处理，避免同时切换物品栏。
        var input = new ClientInput.Scroll(event.getScrollDelta());
        ClientEditModeController.handleMouseScroll(input);
        if (input.consumed()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        var input = new ClientInput.Button(event.getButton(), event.getAction());
        SelectionClientController.handleMouseButton(input);
        if (input.consumed()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        var input = new ClientInput.Highlight(event.getCamera());
        SelectionClientController.handleRenderBlockHighlight(input);
        if (input.consumed()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (ClientEditModeController.isEditing() && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
            // 编辑模式允许穿墙，方块内遮罩应按观察者模式处理，不显示创造模式的窒息全屏贴图。
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES)
            SelectionOverlayRenderer.render(event.getCamera(), event.getPoseStack(), event.getProjectionMatrix());
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER
                || event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL)
            SelectionOverlayRenderer.restoreDeferredProjectionDepth();
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        // Hide only the crosshair while the wheel or a gizmo drag owns the pointer.
        if (Minecraft.getInstance().screen instanceof RadialMenuScreen<?>
                || (ClientEditModeController.isEditing()
                && EditSubModeState.supports(EditSubMode.Capability.TRANSLATE)
                && SelectionClientController.gizmoManager().isHolded())) {
            event.setCanceled(true);
        }
    }

}
