package com.redawn.worldeditpp.bridge.minecraft.client.mode;



import com.redawn.worldeditpp.bridge.minecraft.client.mode.DrawModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;

import com.redawn.worldeditpp.mode.DrawMode;
import com.redawn.worldeditpp.bridge.minecraft.network.DrawSelectionPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrawModeProtocolRenderTest {
    @org.junit.jupiter.api.BeforeAll
    static void installClientPlatform() {
        com.redawn.worldeditpp.bridge.RenderTestPlatform.install();
    }

    @Test void wheelOpensOnlyOnAnEligiblePlainKeyPress() {
        assertTrue(DrawModeController.shouldOpenWheel(false, true, false, true));
        assertFalse(DrawModeController.shouldOpenWheel(false, true, true, true), "Ctrl+V remains paste");
        assertFalse(DrawModeController.shouldOpenWheel(true, true, false, true),
                "Releasing Ctrl or closing with Escape while V stays held must not reopen the wheel");
        assertFalse(DrawModeController.shouldOpenWheel(true, false, false, true));
        assertFalse(DrawModeController.shouldOpenWheel(false, true, false, false));
        assertTrue(WorldEditPlusPlusKeyMappings.DRAW_WHEEL.get().matches(org.lwjgl.glfw.GLFW.GLFW_KEY_V, -1));
    }

    @Test void everyModeRetainsClickedFaceAndPlacementHalf() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (DrawMode mode : DrawMode.values()) for (Direction face : Direction.values()) {
                for (boolean upper : new boolean[]{false, true}) {
                    var payload = new DrawSelectionPayload(face, upper, mode);
                    DrawSelectionPayload.STREAM_CODEC.encode(buffer, payload);
                    assertEquals(payload, DrawSelectionPayload.STREAM_CODEC.decode(buffer));
                    assertFalse(buffer.isReadable());
                }
            }
            buffer.writeByte(Direction.UP.get3DDataValue());
            buffer.writeBoolean(false);
            buffer.writeUtf("unknown", 32);
            assertEquals(DrawSelectionPayload.invalid(), DrawSelectionPayload.STREAM_CODEC.decode(buffer));

            var empty = new FriendlyByteBuf(Unpooled.buffer());
            try {
                assertEquals(DrawSelectionPayload.invalid(), DrawSelectionPayload.STREAM_CODEC.decode(empty));
            } finally {
                empty.release();
            }
        } finally {
            buffer.release();
        }
    }
}
