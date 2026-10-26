package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.config.WorldEditPlusPlusConfig;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.event.platform.ConfigurationLoadEvent;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;

public final class WorldEditHooks {
    private static final String DEFAULT_WAND = "worldeditpp:worldeditpp_wand";
    private static final String DISABLED_NAVIGATION_WAND = "worldeditpp:disabled_navigation_wand";
    private static final WorldEditHooks INSTANCE = new WorldEditHooks();
    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }

        // WorldEdit 使用自己的事件总线；Forge 的 @SubscribeEvent 不会接收到这些事件。
        WorldEditBridge.worldEdit().getEventBus().register(INSTANCE);
        registered = true;
        WorldEditPlusPlus.LOGGER.info("WorldEdit++ 已注册 WorldEdit EditSession 扩展挂钩。");
    }

    @Subscribe
    public void onConfigurationLoad(ConfigurationLoadEvent event) {
        applyToolConfiguration(event.getConfiguration());
    }

    @Subscribe
    public void onPlatformReady(PlatformReadyEvent event) {
        applyCurrentToolConfiguration();
    }

    private static void applyCurrentToolConfiguration() {
        applyToolConfiguration(WorldEditBridge.worldEdit().getConfiguration());
    }

    private static void applyToolConfiguration(com.sk89q.worldedit.LocalConfiguration configuration) {
        configuration.wandItem = DEFAULT_WAND;
        configuration.navigationWand = DISABLED_NAVIGATION_WAND;
        configuration.navigationWandMaxDistance = 0;
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if ((event.getStage() == EditSession.Stage.BEFORE_CHANGE || event.getStage() == EditSession.Stage.BEFORE_HISTORY)
                && !HistoryGate.mayCreateEdit(event.getActor())) {
            // WorldEdit's event bus catches listener exceptions. Reject at the actual
            // write boundary, before history recording AND before raw writes.
            event.setExtent(new com.redawn.worldeditpp.bridge.worldedit.history.HistoryLockedExtent(event.getExtent()));
            return;
        }
        if (!WorldEditPlusPlusConfig.ENABLE_DEBUG_LOG.get()) {
            return;
        }
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY) {
            return;
        }

        Actor actor = event.getActor();
        if (actor == null) {
            return;
        }

        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws com.sk89q.worldedit.WorldEditException {
                WorldEditPlusPlus.LOGGER.debug("WorldEdit 操作者 {} 在 {} 放置/替换为 {}。", actor.getName(), location, block.getBlockType().getId());
                return super.setBlock(location, block);
            }
        });
    }

    private WorldEditHooks() {
        // WorldEdit 事件监听器使用单例注册，避免重复挂载导致逻辑执行多次。
    }
}
