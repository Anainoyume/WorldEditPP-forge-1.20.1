package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionFaceHit;
import com.redawn.common.selection.SelectionFaceRaycaster;
import com.redawn.common.selection.SelectionVector;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class SelectionInteractionController {
    private static final SelectionFaceRaycaster RAYCASTER = new SelectionFaceRaycaster();

    public static void updateHover(Minecraft minecraft, Camera camera, SelectionOverlayState state) {
        updateHover(minecraft, camera, state, isAltDown(minecraft));
    }

    public static void updateHover(
            Minecraft minecraft,
            Camera camera,
            SelectionOverlayState state,
            boolean backFaces
    ) {
        if (!state.hasCuboidSelection() || minecraft.player == null) {
            state.resetInteraction();
            return;
        }

        state.updateEmptyHand(minecraft.player.getMainHandItem().isEmpty());

        Vector3f look = camera.getLookVector();
        Vec3 direction = new Vec3(look.x(), look.y(), look.z());
        AABB box = state.cuboidSelectionBox();
        Optional<SelectionFaceHit> hover = RAYCASTER.pick(toSelectionBox(box), toSelectionVector(camera.getPosition()), toSelectionVector(direction), backFaces);
        state.updateHover(hover);
    }

    public static boolean selectHoveredFace(SelectionOverlayState state) {
        Optional<SelectionFaceHit> hover = state.hoverHit();
        if (hover.isEmpty()) {
            return false;
        }

        SelectionFace face = hover.get().face();
        if (state.selectedFace() == face) {
            state.selectFace(null);
            WorldEditPlusPlus.LOGGER.debug("WorldEdit++ 选区面已取消：{}", face);
        } else {
            state.selectFace(face);
            WorldEditPlusPlus.LOGGER.debug("WorldEdit++ 选区面已选中：{}", face);
        }
        return true;
    }

    private static boolean isAltDown(Minecraft minecraft) {
        return ClientKeys.isAltDown(minecraft);
    }

    private static SelectionBox toSelectionBox(AABB box) {
        return new SelectionBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static SelectionVector toSelectionVector(Vec3 vector) {
        return new SelectionVector(vector.x, vector.y, vector.z);
    }

    private SelectionInteractionController() {
    }
}
