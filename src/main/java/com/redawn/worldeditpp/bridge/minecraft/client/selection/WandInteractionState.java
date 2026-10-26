package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class WandInteractionState {
    private static final double ANIMATION_SPEED = 0.30D;
    private static final double ANIMATION_SNAP_THRESHOLD = 0.01D;
    private static final double DEFAULT_DISTANCE = 3.0D;
    private static final double RAY_EPSILON = 1.0E-7D;

    private boolean holdingWand;
    private double wandDistance = DEFAULT_DISTANCE;
    private BlockPos wandTargetPos = BlockPos.ZERO;
    private boolean initialised;

    private boolean animating;
    private double animX, animY, animZ;

    public void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            holdingWand = false;
            return;
        }
        holdingWand = isHoldingWandItem(player);
        if (!holdingWand) {
            initialised = false;
            animating = false;
        }
    }

    public boolean isHoldingWand() {
        return holdingWand;
    }

    public double wandDistance() {
        return wandDistance;
    }

    public void adjustDistance(double scrollDelta) {
        if (scrollDelta > 0) {
            wandDistance += 1.0D;
        } else {
            wandDistance = Math.max(1.0D, wandDistance - 1.0D);
        }
    }

    public void resetDistance() {
        wandDistance = DEFAULT_DISTANCE;
    }

    public void alignTo(Minecraft minecraft, BlockPos targetPos) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();
        wandDistance = distanceThroughBlock(eyePos, lookDir, targetPos);
        wandTargetPos = targetPos.immutable();
        animX = wandTargetPos.getX();
        animY = wandTargetPos.getY();
        animZ = wandTargetPos.getZ();
        initialised = true;
        animating = false;
    }

    public BlockPos computeTargetPos(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) return wandTargetPos;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle().normalize();

        Vec3 target = eyePos.add(lookDir.scale(wandDistance));
        BlockPos newTarget = BlockPos.containing(target);

        if (!initialised) {
            wandTargetPos = newTarget;
            animX = newTarget.getX();
            animY = newTarget.getY();
            animZ = newTarget.getZ();
            initialised = true;
            animating = false;
            return wandTargetPos;
        }

        if (!newTarget.equals(wandTargetPos)) {
            if (!animating) {
                animX = wandTargetPos.getX();
                animY = wandTargetPos.getY();
                animZ = wandTargetPos.getZ();
                animating = true;
            }
            wandTargetPos = newTarget;
        }
        return wandTargetPos;
    }

    public BlockPos smoothTargetPos() {
        if (!animating) return wandTargetPos;
        animX = lerp(animX, wandTargetPos.getX(), ANIMATION_SPEED);
        animY = lerp(animY, wandTargetPos.getY(), ANIMATION_SPEED);
        animZ = lerp(animZ, wandTargetPos.getZ(), ANIMATION_SPEED);
        double dx = animX - wandTargetPos.getX();
        double dy = animY - wandTargetPos.getY();
        double dz = animZ - wandTargetPos.getZ();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) < ANIMATION_SNAP_THRESHOLD) {
            animating = false;
            return wandTargetPos;
        }
        int rx = (int) Math.round(animX);
        int ry = (int) Math.round(animY);
        int rz = (int) Math.round(animZ);
        return new BlockPos(rx, ry, rz);
    }

    public double smoothRenderX() {
        if (!animating) return wandTargetPos.getX() + 0.5D;
        return animX + 0.5D;
    }

    public double smoothRenderY() {
        if (!animating) return wandTargetPos.getY() + 0.5D;
        return animY + 0.5D;
    }

    public double smoothRenderZ() {
        if (!animating) return wandTargetPos.getZ() + 0.5D;
        return animZ + 0.5D;
    }

    public void reset() {
        holdingWand = false;
        wandDistance = DEFAULT_DISTANCE;
        wandTargetPos = BlockPos.ZERO;
        initialised = false;
        animating = false;
        animX = 0.0D;
        animY = 0.0D;
        animZ = 0.0D;
    }

    private static boolean isHoldingWandItem(LocalPlayer player) {
        if (player.getMainHandItem().isEmpty()) return false;
        return player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get());
    }

    private static double lerp(double current, double target, double speed) {
        if (Math.abs(target - current) < ANIMATION_SNAP_THRESHOLD) return target;
        return current + (target - current) * speed;
    }

    private static double distanceThroughBlock(Vec3 origin, Vec3 direction, BlockPos targetPos) {
        double near = 0.0D;
        double far = Double.POSITIVE_INFINITY;

        double[] origins = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] minimums = {targetPos.getX(), targetPos.getY(), targetPos.getZ()};

        for (int axis = 0; axis < 3; axis++) {
            double axisDirection = directions[axis];
            double minimum = minimums[axis];
            double maximum = minimum + 1.0D;
            if (Math.abs(axisDirection) < RAY_EPSILON) {
                if (origins[axis] < minimum || origins[axis] > maximum) {
                    return projectedCenterDistance(origin, direction, targetPos);
                }
                continue;
            }

            double first = (minimum - origins[axis]) / axisDirection;
            double second = (maximum - origins[axis]) / axisDirection;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) {
                return projectedCenterDistance(origin, direction, targetPos);
            }
        }

        if (!Double.isFinite(far)) {
            return projectedCenterDistance(origin, direction, targetPos);
        }
        return Math.max(0.0D, (near + far) * 0.5D);
    }

    private static double projectedCenterDistance(Vec3 origin, Vec3 direction, BlockPos targetPos) {
        Vec3 center = Vec3.atCenterOf(targetPos);
        return Math.max(0.0D, center.subtract(origin).dot(direction));
    }
}
