package com.redawn.worldeditpp.bridge.forge.client;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import net.minecraftforge.fml.ModList;

/** Non-invasive diagnostics for the renderer mods that most often change GL state. */
public final class ClientCompatibility {
    public static void logEnvironment() {
        boolean iris = loaded("iris") || loaded("oculus");
        boolean sodium = loaded("sodium") || loaded("embeddium");
        WorldEditPlusPlus.LOGGER.info("Client renderer environment: iris={}, sodium={}", iris, sodium);
        if (iris) {
            WorldEditPlusPlus.LOGGER.info("Shader pack detected; projection uses vanilla RenderType state and "
                    + "will be verified by the render regression matrix.");
        }
    }

    private static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private ClientCompatibility() { }
}
