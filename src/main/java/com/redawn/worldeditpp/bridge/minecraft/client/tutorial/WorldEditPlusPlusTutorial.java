package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import net.minecraft.client.Minecraft;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/** WorldEdit++ ownership adapter for the reusable guide engine. */
public final class WorldEditPlusPlusTutorial {
    public static final TutorialHost HOST = new TutorialHost(
            new ResourceLocation(WorldEditPlusPlus.MOD_ID, "tutorial"),
            WorldEditPlusPlus.MOD_ID,
            "tutorial.worldeditpp.",
            "worldeditpp/tutorial.properties",
            List.of("worldeditpp-tutorial.properties"),
            () -> WorldEditPlusPlusKeyMappings.ACCESSIBILITY.get().getTranslatedKeyMessage(),
            (key, scan) -> WorldEditPlusPlusKeyMappings.ACCESSIBILITY.get().matches(key, scan),
            () -> Minecraft.getInstance().level != null,
            WorldEditPlusPlus.LOGGER
    );

    public static void open(Screen parent) {
        TutorialRuntime.open(HOST, parent);
    }

    public static void open(Screen parent, String initialPage) {
        TutorialRuntime.open(HOST, parent, initialPage);
    }

    private WorldEditPlusPlusTutorial() { }
}
