package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import net.minecraft.resources.ResourceLocation;

/** Only local book destinations can be followed; never a chat command or external URL. */
public final class TutorialNavigation {
    public record Link(ResourceLocation source, String path, TutorialText tip) { }
    private TutorialNavigation() { }
}
