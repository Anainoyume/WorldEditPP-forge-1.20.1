package com.redawn.worldeditpp.bridge.minecraft.client.mode;



import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditModeKeyInterceptor;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class KeyReleaseRenderTest {
    @Test void closingScreenReleaseClearsModKeysAndReachesVanillaRegardlessOfContext() {
        for (boolean singlePress : new boolean[]{false, true}) {
            String id = "test.worldeditpp.release." + singlePress;
            KeyMapping mapping = new KeyMapping(id, GLFW.GLFW_KEY_X, "key.categories.misc") {
                @Override public boolean isConflictContextAndModifierActive() { return true; }
            };
            if (singlePress) {
                EditModeKeyInterceptor.registerSinglePress(id, mapping,
                        EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                        ignored -> fail("A release must not trigger an action"),
                        () -> { throw new AssertionError("Releases must not depend on the screen or edit context"); });
            } else {
                EditModeKeyInterceptor.register(id, mapping,
                        EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                        ignored -> fail("A release must not trigger an action"),
                        () -> { throw new AssertionError("Releases must not depend on the screen or edit context"); });
            }
            // MouseHandler.grabMouse re-samples a physically held X when closing a screen.
            mapping.setDown(true);
            assertTrue(mapping.isDown());
            assertFalse(EditModeKeyInterceptor.onRawKeySet(
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_X), false),
                    "The mixin must let KeyMapping.set release vanilla's load-hotbar binding too");
            assertFalse(mapping.isDown());
        }
    }
}
