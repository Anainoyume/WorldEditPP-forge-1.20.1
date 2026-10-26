package com.redawn.worldeditpp.bridge.minecraft.network;



import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardConfirmationRenderTest {
    @Test void confirmationRetainsAirPolicyAndSignedCoordinates() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (boolean ignoreAir : new boolean[]{false, true}) {
                var request = ClipboardAdjustmentPayload.confirm(-190, -64, 32769, ignoreAir);
                ClipboardAdjustmentPayload.STREAM_CODEC.encode(buffer, request);
                var received = ClipboardAdjustmentPayload.STREAM_CODEC.decode(buffer);
                assertEquals(request, received);
                assertEquals(ignoreAir ? ClipboardAdjustmentPayload.Action.CONFIRM_IGNORE_AIR
                        : ClipboardAdjustmentPayload.Action.CONFIRM, received.action());
                assertFalse(buffer.isReadable());
            }
            assertEquals(7, ClipboardAdjustmentPayload.Action.CONFIRM.ordinal());
            assertEquals(8, ClipboardAdjustmentPayload.Action.MIRROR.ordinal());
        } finally {
            buffer.release();
        }
    }
}
