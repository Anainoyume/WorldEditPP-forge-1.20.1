package dev.worldeditpp.testing;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import java.lang.reflect.Method;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Regression for non-cuboid regions accidentally becoming their bounding cuboids. */
@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class RegionSectionCursorGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nonCuboidRegionUsesRegionContains(GameTestHelper helper) throws Exception {
        Polygonal2DRegion triangle = new Polygonal2DRegion();
        triangle.addPoint(BlockVector2.at(0, 0));
        triangle.addPoint(BlockVector2.at(4, 0));
        triangle.addPoint(BlockVector2.at(0, 4));
        triangle.setMinimumY(0);
        triangle.setMaximumY(0);

        long volume = triangle.getVolume();
        long boundingVolume = 5L * 5L;
        helper.assertTrue(volume > 0 && volume < boundingVolume,
                "test polygon must be smaller than its bounding box: " + volume);

        Method accepts = Class.forName(
                        "com.redawn.worldeditpp.bridge.worldedit.operation.RegionSectionCursor")
                .getDeclaredMethod("accepts",
                        com.sk89q.worldedit.regions.Region.class, boolean.class, BlockVector3.class);
        accepts.setAccessible(true);
        helper.assertTrue((boolean) accepts.invoke(null,
                        triangle, false, BlockVector3.at(1, 0, 1)),
                "an inside polygon cell was rejected");
        helper.assertFalse((boolean) accepts.invoke(null,
                        triangle, false, BlockVector3.at(4, 0, 4)),
                "an outside polygon cell was accepted");
        helper.assertTrue((boolean) accepts.invoke(null,
                        triangle, true, BlockVector3.at(4, 0, 4)),
                "the cuboid fast path must remain exact-by-bounds");

        ConvexPolyhedralRegion tetrahedron = new ConvexPolyhedralRegion(
                (com.sk89q.worldedit.world.World) null);
        tetrahedron.addVertex(BlockVector3.at(0, 0, 0));
        tetrahedron.addVertex(BlockVector3.at(4, 0, 0));
        tetrahedron.addVertex(BlockVector3.at(0, 4, 0));
        tetrahedron.addVertex(BlockVector3.at(0, 0, 4));
        helper.assertTrue(tetrahedron.isDefined(), "test tetrahedron is incomplete");
        helper.assertTrue((boolean) accepts.invoke(null,
                        tetrahedron, false, BlockVector3.at(1, 1, 1)),
                "an inside convex-polyhedron cell was rejected");
        helper.assertFalse((boolean) accepts.invoke(null,
                        tetrahedron, false, BlockVector3.at(3, 3, 3)),
                "an outside convex-polyhedron cell was accepted");
        helper.succeed();
    }

    private RegionSectionCursorGameTests() { }
}
