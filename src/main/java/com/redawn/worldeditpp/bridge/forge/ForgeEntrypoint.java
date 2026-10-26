package com.redawn.worldeditpp.bridge.forge;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

import com.redawn.worldeditpp.config.WorldEditPlusPlusConfig;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.redawn.worldeditpp.bridge.worldedit.WorldEditHooks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(WorldEditPlusPlus.MOD_ID)
public final class ForgeEntrypoint
{
    public ForgeEntrypoint() {
        IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        // The safe variant validates its supplier even on a dedicated server, resolving Screen.
        // This variant evaluates the client supplier only when the physical side is CLIENT.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> com.redawn.worldeditpp.bridge.forge.client.ForgeClientEntrypoint::initialize);
        com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform.install(
                new com.redawn.worldeditpp.bridge.forge.worldedit.ForgeWorldEditPlatform());
        com.redawn.worldeditpp.bridge.minecraft.api.EditingBridge.install(
                new com.redawn.worldeditpp.bridge.worldedit.WorldEditEditingBackend());
        PlatformBridge.install(new ForgePlatform());
        ModItems.install(ForgeItems.AIR_BLOCK, ForgeItems.PACKED_BLOCK, ForgeItems.WORLD_EDIT_WAND);
        WorldEditPlusPlusConfig.install(ForgeConfiguration.ENABLE_DEBUG_LOG::get);
        ForgeItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        ForgeNetworkRegistration.registerPayloads();
        modEventBus.addListener(ForgeItems::onBuildCreativeTab);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfiguration.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WorldEditHooks::register);
        WorldEditPlusPlus.LOGGER.info("{} 初始化完成，WorldEdit 扩展挂钩已准备注册。", WorldEditPlusPlus.MOD_NAME);
    }
}
