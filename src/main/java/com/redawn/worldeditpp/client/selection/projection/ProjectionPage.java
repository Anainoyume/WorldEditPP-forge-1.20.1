package com.redawn.worldeditpp.client.selection.projection;

/** Includes a one-cell neighbour halo in validity checks, so stretching rebuilds seams only. */
public record ProjectionPage(int x, int y, int z, int boundX, int boundY, int boundZ) {
    public static final int EDGE = 16;
    public static ProjectionPage at(int x, int y, int z, int sx, int sy, int sz) {
        return new ProjectionPage(x, y, z, Math.min(sx, x + EDGE + 1),
                Math.min(sy, y + EDGE + 1), Math.min(sz, z + EDGE + 1));
    }
    public boolean matches(int sx, int sy, int sz) {
        return x < sx && y < sy && z < sz && equals(at(x, y, z, sx, sy, sz));
    }
    public long key() { return ((long) (x / EDGE) << 42) | ((long) (y / EDGE) << 21) | (z / EDGE); }
    public int width() { return Math.min(EDGE, boundX - x); }
    public int height() { return Math.min(EDGE, boundY - y); }
    public int depth() { return Math.min(EDGE, boundZ - z); }
}
