package com.redawn.worldeditpp.mode;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/** Stable identity and capabilities for the persistent selection editing mode. */
public enum EditSubMode {
    MOVE("move", 0, Capability.TRANSLATE),
    STRETCH("stretch", 1, Capability.STRETCH),
    ROTATE("rotate", 2, Capability.ROTATE);

    public enum Capability {
        TRANSLATE,
        STRETCH,
        ROTATE
    }

    private final String id;
    private final int networkValue;
    private final EnumSet<Capability> capabilities;

    EditSubMode(String id, int networkValue, Capability capability) {
        this.id = id;
        this.networkValue = networkValue;
        this.capabilities = EnumSet.of(capability);
    }

    public String id() {
        return id;
    }

    public int networkValue() {
        return networkValue;
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    public static EditSubMode fromId(String id) {
        if (id == null) {
            return MOVE;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "move", "selection" -> MOVE;
            case "stretch", "build" -> STRETCH;
            case "rotate" -> ROTATE;
            default -> MOVE;
        };
    }

    public static Optional<EditSubMode> fromNetworkValue(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            return Optional.empty();
        }
        int code = (int) value;
        for (EditSubMode mode : values()) {
            if (mode.networkValue == code) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
