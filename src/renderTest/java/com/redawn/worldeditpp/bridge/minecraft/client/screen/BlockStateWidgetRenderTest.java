package com.redawn.worldeditpp.bridge.minecraft.client.screen;



import com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStateEditorScreen;
import com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStatePropertyWidget;

import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditResultPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class BlockStateWidgetRenderTest {
    @Test void arrowsWheelAndDropdownSelectionUseTheSameEnumValues() {
        var changes = new ArrayList<String>();
        var opens = new AtomicInteger();
        var widget = new BlockStatePropertyWidget("facing", List.of("north", "east", "south", "west"),
                "north", changes::add, ignored -> opens.incrementAndGet());
        widget.setX(20);
        widget.setY(10);
        int controlMiddle = widget.controlY() + BlockStatePropertyWidget.CONTROL_HEIGHT / 2;
        widget.onClick(widget.controlX() + widget.controlWidth() - 5, controlMiddle);
        assertEquals("east", widget.value());
        widget.onClick(widget.controlX() + 5, controlMiddle);
        assertEquals("north", widget.value());
        widget.mouseScrolled(30, 40, 0.4);
        widget.mouseScrolled(30, 40, 0.4);
        assertEquals("north", widget.value());
        widget.mouseScrolled(30, 40, 0.4);
        assertEquals("west", widget.value());
        widget.onClick(widget.controlX() + widget.controlWidth() / 2, controlMiddle);
        assertEquals(1, opens.get());
        widget.choose("south");
        widget.choose("invalid");
        assertEquals("south", widget.value());
        widget.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        assertEquals("west", widget.value());
        assertEquals(List.of("east", "north", "west", "south", "west"), changes);
    }

    @Test void statePacketsKeepLockedTargetAndSessionIdentity() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new BlockStateEditPayload(12345, 99, new ResourceLocation("minecraft:overworld"),
                    new BlockPos(-9, 72, 33), new ResourceLocation("minecraft:oak_stairs"), "half", "top");
            var result = new BlockStateEditResultPayload(12345, 99, 654, BlockStateEditResultPayload.Status.APPLIED);
            BlockStateEditPayload.STREAM_CODEC.encode(buffer, request);
            BlockStateEditResultPayload.STREAM_CODEC.encode(buffer, result);
            assertEquals(request, BlockStateEditPayload.STREAM_CODEC.decode(buffer));
            assertEquals(result, BlockStateEditResultPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test void scrollbarDragPreservesGrabOffsetAndClampsBeyondTrack() {
        assertEquals(0, BlockStateEditorScreen.scrollbarScroll(55, 50, 200, 40, 5, 600));
        assertEquals(300, BlockStateEditorScreen.scrollbarScroll(135, 50, 200, 40, 5, 600));
        assertEquals(600, BlockStateEditorScreen.scrollbarScroll(215, 50, 200, 40, 5, 600));
        assertEquals(0, BlockStateEditorScreen.scrollbarScroll(-100, 50, 200, 40, 5, 600));
        assertEquals(600, BlockStateEditorScreen.scrollbarScroll(900, 50, 200, 40, 5, 600));
        assertEquals(0, BlockStateEditorScreen.scrollbarScroll(80, 50, 40, 40, 5, 0));
    }

    @Test void wallConnectionsPrecedeUpAndWaterloggedStaysLast() {
        var ordered = List.of("waterlogged", "up", "south", "east", "west", "north").stream()
                .sorted(java.util.Comparator.<String>comparingInt(name -> BlockStateEditorScreen.propertyOrder(true, name))
                        .thenComparing(java.util.Comparator.naturalOrder())).toList();
        assertEquals(List.of("east", "north", "south", "west", "up", "waterlogged"), ordered);
        assertEquals(0, BlockStateEditorScreen.propertyOrder(false, "up"));
        assertTrue(BlockStateEditorScreen.propertyOrder(false, "waterlogged")
                > BlockStateEditorScreen.propertyOrder(false, "west"));
    }
}
