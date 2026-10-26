package com.redawn.worldeditpp.client.draw3d;


public enum DrawDirection {
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1);

    private final int nx, ny, nz;

    DrawDirection(int nx, int ny, int nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public int normalX() { return nx; }
    public int normalY() { return ny; }
    public int normalZ() { return nz; }

    public double axisComponent(double x, double y, double z) {
        return nx != 0 ? x * nx : (ny != 0 ? y * ny : z * nz);
    }

    public boolean isXAxis() { return nx != 0; }
    public boolean isYAxis() { return ny != 0; }
    public boolean isZAxis() { return nz != 0; }
    public boolean isPositive() { return nx > 0 || ny > 0 || nz > 0; }
}
