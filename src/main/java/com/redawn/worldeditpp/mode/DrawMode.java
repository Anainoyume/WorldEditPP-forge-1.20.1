package com.redawn.worldeditpp.mode;

/** Transient drawing shape, independent of selection submodes and replacement filters. */
public enum DrawMode {
    SET("set"),
    WALLS("walls");

    private final String id;

    DrawMode(String id) { this.id = id; }
    public String id() { return id; }

    public static DrawMode fromId(String id) {
        for (DrawMode mode : values()) if (mode.id.equals(id)) return mode;
        throw new IllegalArgumentException("Unknown draw mode: " + id);
    }
}
