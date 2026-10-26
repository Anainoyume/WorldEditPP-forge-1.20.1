package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionTransforms;



import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.NudgeSelectionPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionPredictionRenderTest {
    @Test
    void animationMustNotFallBackToAnOlderServerSelection() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 5, 3, 3);
        long firstRequest = state.beginSelectionRequest();
        state.tickDisplayAnimation();
        state.nudgeTo(0, 0, 0, 12, 3, 3);
        state.beginSelectionRequest();
        state.updateCuboidSelection(selection(5).withAcknowledgedRequest(firstRequest));
        double previous = state.cuboidSelectionBox().maxX;
        for (int frame = 0; frame < 100; frame++) {
            state.tickDisplayAnimation();
            double current = state.cuboidSelectionBox().maxX;
            assertTrue(current >= previous, "An old acknowledgement must never move the face backwards");
            previous = current;
        }
        assertEquals(13, state.cuboidSelectionBox().maxX);
        assertEquals(12, state.currentSelectionBox().maxX());
    }

    @Test
    void delayedIntermediateAcknowledgementMustNotDeselectTheFace() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 5, 3, 3);
        long firstRequest = state.beginSelectionRequest();
        state.nudgeTo(0, 0, 0, 12, 3, 3);
        long lastRequest = state.beginSelectionRequest();
        for (int frame = 0; frame < 100; frame++) state.tickDisplayAnimation();
        state.updateCuboidSelection(selection(5).withAcknowledgedRequest(firstRequest));
        assertEquals(SelectionFace.EAST, state.selectedFace());
        assertEquals(12, state.currentSelectionBox().maxX());
        state.updateCuboidSelection(selection(12).withAcknowledgedRequest(lastRequest));
        assertEquals(SelectionFace.EAST, state.selectedFace());
        assertEquals(12, state.currentSelectionBox().maxX());
    }

    @Test
    void nextWheelInputAfterAnimationSettlesUsesTheUnacknowledgedTarget() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 12, 3, 3);
        long firstRequest = state.beginSelectionRequest();
        settle(state);
        state.nudgeTo(0, 0, 0, (int) state.currentSelectionBox().maxX() + 1, 3, 3);
        long lastRequest = state.beginSelectionRequest();
        state.updateCuboidSelection(selection(12).withAcknowledgedRequest(firstRequest));
        settle(state);
        state.updateCuboidSelection(selection(13).withAcknowledgedRequest(lastRequest));
        assertEquals(13, state.currentSelectionBox().maxX());
        assertEquals(14, state.cuboidSelectionBox().maxX);
        assertEquals(13, state.pos2X());
        assertEquals(SelectionFace.EAST, state.selectedFace());
    }

    @Test
    void matchingAcknowledgementDoesNotSnapAnUnfinishedAnimation() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 12, 3, 3);
        long request = state.beginSelectionRequest();
        state.tickDisplayAnimation();
        double displayed = state.cuboidSelectionBox().maxX;
        state.updateCuboidSelection(selection(12).withAcknowledgedRequest(request));
        assertEquals(displayed, state.cuboidSelectionBox().maxX);
        assertTrue(state.isSelectionTransformInProgress());
        settle(state);
        assertEquals(13, state.cuboidSelectionBox().maxX);
        assertEquals(SelectionFace.EAST, state.selectedFace());
    }

    @Test
    void repeatedCoordinatesCannotAcknowledgeANewerRequest() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 5, 3, 3);
        long first = state.beginSelectionRequest();
        state.nudgeTo(0, 0, 0, 8, 3, 3);
        long middle = state.beginSelectionRequest();
        state.nudgeTo(0, 0, 0, 5, 3, 3);
        long last = state.beginSelectionRequest();
        state.updateCuboidSelection(selection(5).withAcknowledgedRequest(first));
        settle(state);
        state.updateCuboidSelection(selection(8).withAcknowledgedRequest(middle));
        assertEquals(5, state.currentSelectionBox().maxX());
        assertEquals(SelectionFace.EAST, state.selectedFace());
        state.updateCuboidSelection(selection(5).withAcknowledgedRequest(last));
        assertEquals(6, state.cuboidSelectionBox().maxX);
    }

    @Test
    void finalRejectionAndExternalChangesStillReplacePrediction() {
        SelectionOverlayState state = selectedState();
        state.nudgeTo(0, 0, 0, 12, 3, 3);
        long request = state.beginSelectionRequest();
        state.tickDisplayAnimation();
        state.updateCuboidSelection(selection(3).withAcknowledgedRequest(request));
        assertEquals(3, state.currentSelectionBox().maxX());
        assertFalse(state.isSelectionTransformInProgress());
        assertNull(state.selectedFace());
        state.selectFace(SelectionFace.EAST);
        state.updateCuboidSelection(selection(7).withAcknowledgedRequest(request));
        assertEquals(7, state.currentSelectionBox().maxX());
        assertNull(state.selectedFace());
        state.updateCuboidSelection(CuboidSelectionPayload.hidden().withAcknowledgedRequest(request));
        assertFalse(state.hasCuboidSelection());
    }

    @Test
    void delayedRotationAcknowledgementPreservesNewFaceSelection() {
        SelectionOverlayState state = selectedState();
        var rotation = SelectionTransforms.rotate(state.currentSelectionBox(),
                0, 0, 0, 3, 3, 3, SelectionFace.UP, false);
        assertTrue(state.rotateTo(rotation, 0));
        long request = state.beginSelectionRequest();
        state.tickDisplayAnimation(200_000_000L);
        state.selectFace(SelectionFace.EAST);
        var box = rotation.box();
        state.updateCuboidSelection(new CuboidSelectionPayload(true,
                (int) box.minX(), (int) box.minY(), (int) box.minZ(),
                (int) box.maxX(), (int) box.maxY(), (int) box.maxZ(),
                rotation.pos1X(), rotation.pos1Y(), rotation.pos1Z(),
                rotation.pos2X(), rotation.pos2Y(), rotation.pos2Z(), request));
        assertEquals(SelectionFace.EAST, state.selectedFace());
    }

    @Test
    void localAdjustmentStillSettlesWithoutServerAcknowledgements() {
        SelectionOverlayState state = selectedState();
        state.nudgeLocalTo(0, 0, 0, 12, 3, 3);
        settle(state);
        assertEquals(13, state.cuboidSelectionBox().maxX);
        assertEquals(SelectionFace.EAST, state.selectedFace());
    }

    @Test
    void allSixFacesMatchLocalAdjustmentUnderBurstsReversalsAndDelayedBatchedReplies() {
        for (SelectionFace face : SelectionFace.values()) {
            SelectionOverlayState remote = selectedState();
            SelectionOverlayState local = selectedState();
            remote.selectFace(face);
            local.selectFace(face);
            var replies = new ArrayList<CuboidSelectionPayload>();
            for (int step = 0; step < 80; step++) {
                SelectionBox base = local.currentSelectionBox();
                int sign = (int) (face.normalX() + face.normalY() + face.normalZ());
                SelectionBox target = base.withFace(face, face.plane(base) + sign * (step < 40 ? 1 : -1));
                remote.nudgeTo((int) target.minX(), (int) target.minY(), (int) target.minZ(),
                        (int) target.maxX(), (int) target.maxY(), (int) target.maxZ());
                local.nudgeLocalTo((int) target.minX(), (int) target.minY(), (int) target.minZ(),
                        (int) target.maxX(), (int) target.maxY(), (int) target.maxZ());
                replies.add(remote.animationTarget().withAcknowledgedRequest(remote.beginSelectionRequest()));
                if (step % 4 == 0) remote.updateCuboidSelection(replies.get(step / 2));
                int frames = step % 13 == 0 ? 100 : 1;
                for (int frame = 0; frame < frames; frame++) {
                    remote.tickDisplayAnimation();
                    local.tickDisplayAnimation();
                    assertEquals(local.cuboidSelectionBox(), remote.cuboidSelectionBox());
                    assertEquals(local.smoothPos1X(), remote.smoothPos1X());
                    assertEquals(local.smoothPos2X(), remote.smoothPos2X());
                }
                assertEquals(target, remote.currentSelectionBox());
                assertEquals(face, remote.selectedFace());
            }
            remote.updateCuboidSelection(replies.get(replies.size() - 1));
            settle(remote);
            settle(local);
            assertEquals(local.cuboidSelectionBox(), remote.cuboidSelectionBox());
            assertEquals(new SelectionBox(0, 0, 0, 3, 3, 3), remote.currentSelectionBox());
            assertEquals(face, remote.selectedFace());
        }
    }

    @Test
    void requestAndAcknowledgementIdsSurviveNetworkRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            long requestId = (long) Integer.MAX_VALUE + 123;
            NudgeSelectionPayload request = new NudgeSelectionPayload(
                    -5, -4, -3, 7, 8, 9, 7, -4, 9, -5, 8, -3, requestId);
            CuboidSelectionPayload reply = new CuboidSelectionPayload(true,
                    -5, -4, -3, 7, 8, 9, 7, -4, 9, -5, 8, -3, requestId);
            NudgeSelectionPayload.STREAM_CODEC.encode(buffer, request);
            CuboidSelectionPayload.STREAM_CODEC.encode(buffer, reply);
            assertEquals(request, NudgeSelectionPayload.STREAM_CODEC.decode(buffer));
            assertEquals(reply, CuboidSelectionPayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            // An unchanged final box must still produce a sync when a newer edit was processed.
            assertNotEquals(reply, reply.withAcknowledgedRequest(requestId + 1));
        } finally {
            buffer.release();
        }
    }

    private static void settle(SelectionOverlayState state) {
        for (int frame = 0; frame < 100; frame++) state.tickDisplayAnimation();
    }

    private static SelectionOverlayState selectedState() {
        SelectionOverlayState state = new SelectionOverlayState();
        state.replaceCuboidSelection(selection(3));
        state.selectFace(SelectionFace.EAST);
        return state;
    }

    private static CuboidSelectionPayload selection(int maxX) {
        return new CuboidSelectionPayload(true, 0, 0, 0, maxX, 3, 3, 0, 0, 0, maxX, 3, 3);
    }
}
