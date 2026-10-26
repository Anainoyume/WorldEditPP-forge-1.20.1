package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;

@GameTestSuiteDescriptor(name = "CuboidRegion 选区计算")
public final class CuboidRegionTestSuite extends TestSuiteBase {

    @Override
    protected void runTests(GameSimulation simulation) {
        test("构造与坐标提取", this::testConstruction);
        test("整体扩张", this::testExpansion);
        test("单格高Y轴扩展", this::testOneBlockTallY);
    }

    private void testConstruction() {
        CuboidRegion region = new CuboidRegion(
                BlockVector3.at(27, -51, 30),
                BlockVector3.at(26, -51, 32));

        BlockVector3 pos1 = region.getPos1();
        BlockVector3 pos2 = region.getPos2();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        WorldEditPlusPlus.LOGGER.debug("[CuboidRegion] 原始选区 pos1=({},{},{}), pos2=({},{},{})",
                pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());

        GameAssertions.check(27 == pos1.getX(), "pos1X 必须等于构造第一个参数x");
        GameAssertions.check(26 == pos2.getX(), "pos2X 必须等于构造第二个参数x");
        GameAssertions.check(26 == min.getX() && 27 == max.getX(), "X范围: min=26, max=27");
        GameAssertions.check(-51 == min.getY() && -51 == max.getY(), "Y范围: 单格高层 min=max=-51");
        GameAssertions.check(30 == min.getZ() && 32 == max.getZ(), "Z范围: min=30, max=32");
    }

    private void testExpansion() {
        CuboidRegion expanded = new CuboidRegion(
                BlockVector3.at(25, -52, 29),
                BlockVector3.at(28, -50, 33));

        BlockVector3 eMin = expanded.getMinimumPoint();
        BlockVector3 eMax = expanded.getMaximumPoint();

        GameAssertions.check(25 == eMin.getX(), "扩张minX=25");
        GameAssertions.check(28 == eMax.getX(), "扩张maxX=28");
        GameAssertions.check(-52 == eMin.getY(), "扩张minY=-52");
        GameAssertions.check(-50 == eMax.getY(), "扩张maxY=-50");
        GameAssertions.check(29 == eMin.getZ(), "扩张minZ=29");
        GameAssertions.check(33 == eMax.getZ(), "扩张maxZ=33");

        BlockVector3 ePos1 = expanded.getPos1();
        BlockVector3 ePos2 = expanded.getPos2();

        GameAssertions.check(ePos1.getX() == 25, "构造pos1X=25(min)");
        GameAssertions.check(ePos2.getX() == 28, "构造pos2X=28(max)");
        GameAssertions.check(ePos1.getZ() == 29, "构造pos1Z=29(min)");
        GameAssertions.check(ePos2.getZ() == 33, "构造pos2Z=33(max)");
    }

    private void testOneBlockTallY() {
        CuboidRegion original = new CuboidRegion(
                BlockVector3.at(26, -51, 30),
                BlockVector3.at(27, -51, 32));

        CuboidRegion expanded = new CuboidRegion(
                BlockVector3.at(25, -52, 29),
                BlockVector3.at(28, -50, 33));

        BlockVector3 ePos1 = expanded.getPos1();
        BlockVector3 ePos2 = expanded.getPos2();

        GameAssertions.check(ePos1.getY() == -52 && ePos2.getY() == -50,
                "1格高扩张: pos1Y应=-52(min), pos2Y应=-50(max). 实际pos1Y=" + ePos1.getY() + " pos2Y=" + ePos2.getY());
    }
}
