package com.redawn.common.selection;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class SelectionInteractionState {
    private static final float ANIMATION_STEP = 0.12F;

    private Optional<SelectionFaceHit> hoverHit = Optional.empty();
    private SelectionFace selectedFace;
    private boolean emptyHand;
    private final Map<SelectionFace, Float> hoverProgress = new EnumMap<>(SelectionFace.class);
    private final Map<SelectionFace, Float> selectedProgress = new EnumMap<>(SelectionFace.class);

    public Optional<SelectionFaceHit> hoverHit() {
        return hoverHit;
    }

    public SelectionFace selectedFace() {
        return selectedFace;
    }

    public boolean isEmptyHand() {
        return emptyHand;
    }

    public void updateEmptyHand(boolean emptyHand) {
        this.emptyHand = emptyHand;
    }

    public void updateHover(Optional<SelectionFaceHit> hoverHit) {
        this.hoverHit = hoverHit;
    }

    public void selectFace(SelectionFace face) {
        selectedFace = face;
    }

    public float hoverProgress(SelectionFace face) {
        return hoverProgress.getOrDefault(face, 0.0F);
    }

    public float selectedProgress(SelectionFace face) {
        return selectedProgress.getOrDefault(face, 0.0F);
    }

    public void tickAnimations() {
        for (SelectionFace face : SelectionFace.values()) {
            boolean hovering = hoverHit.map(SelectionFaceHit::face).orElse(null) == face && selectedFace != face;
            hoverProgress.put(face, approach(hoverProgress(face), hovering ? 1.0F : 0.0F));
            selectedProgress.put(face, approach(selectedProgress(face), selectedFace == face ? 1.0F : 0.0F));
        }
    }

    public void reset() {
        hoverHit = Optional.empty();
        selectedFace = null;
        hoverProgress.clear();
        selectedProgress.clear();
    }

    private static float approach(float value, float target) {
        if (value < target) {
            return Math.min(target, value + ANIMATION_STEP);
        }
        return Math.max(target, value - ANIMATION_STEP);
    }
}
