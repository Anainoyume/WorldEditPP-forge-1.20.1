package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector3f;

final class TutorialGraphics {
    // 1.20.1 GuiGraphics scissor rectangles ignore the pose stack. Transform them explicitly.
    static void scissor(GuiGraphics g, int left, int top, int right, int bottom) {
        var pose = g.pose().last().pose();
        var min = pose.transformPosition(new Vector3f(left, top, 0));
        var max = pose.transformPosition(new Vector3f(right, bottom, 0));
        g.enableScissor((int) Math.floor(min.x), (int) Math.floor(min.y),
                (int) Math.ceil(max.x), (int) Math.ceil(max.y));
    }
    private TutorialGraphics() { }
}
