package com.redawn.worldeditpp;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/** Shared identity. Loader entrypoints live in bridge.forge. */
public final class WorldEditPlusPlus {
    public static final String MOD_ID = "worldeditpp";
    public static final String MOD_NAME = "WorldEdit++";
    public static final Logger LOGGER = LoggerFactory.getLogger(WorldEditPlusPlus.class);
    private WorldEditPlusPlus() { }
}
