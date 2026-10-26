package dev.worldeditpp.testing;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.worldeditpp.testing.TestHarnessMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class ClipboardTransformGameTests {
    @GameTest(template = "empty", batch = "clipboardTransforms")
    public static void transformedClipboardPreservesOriginAndContent(GameTestHelper helper) throws Exception {
        var origin = BlockVector3.at(10, 70, 20);
        var source = new BlockArrayClipboard(new CuboidRegion(origin, origin.add(2, 0, 0)));
        source.setOrigin(origin);
        source.setBlock(origin, BlockTypes.STONE.getDefaultState());
        source.setBlock(origin.add(2, 0, 0), BlockTypes.GOLD_BLOCK.getDefaultState());
        var holder = new ClipboardHolder(source);
        helper.assertTrue(bake(holder) == source, "Identity must preserve source");
        holder.setTransform(new AffineTransform().rotateY(90));
        var result = bake(holder);
        helper.assertTrue(result.getOrigin().equals(origin), "Origin must remain stable");
        helper.assertTrue(result.getDimensions().equals(BlockVector3.at(1, 1, 3)), "Transformed dimensions");
        helper.assertTrue(result.getBlock(origin).getBlockType() == BlockTypes.STONE, "Origin block preserved");
        var transformed = holder.getTransform().apply(BlockVector3.at(2, 0, 0).toVector3()).toBlockPoint().add(origin);
        helper.assertTrue(result.getBlock(transformed).getBlockType() == BlockTypes.GOLD_BLOCK, "Far corner transformed");
        helper.assertTrue(source.getBlock(origin.add(2, 0, 0)).getBlockType() == BlockTypes.GOLD_BLOCK, "Source immutable");
        helper.succeed();
    }
    private static com.sk89q.worldedit.extent.clipboard.Clipboard bake(ClipboardHolder holder) throws Exception {
        var method = Class.forName("com.redawn.worldeditpp.bridge.worldedit.ClipboardTransforms")
                .getDeclaredMethod("bake", ClipboardHolder.class);
        method.setAccessible(true);
        return (com.sk89q.worldedit.extent.clipboard.Clipboard) method.invoke(null, holder);
    }
    @GameTest(template = "empty", batch = "clipboardTransforms")
    public static void repeatedClipboardUsesOriginalTemplate(GameTestHelper helper) throws Exception {
        var min = BlockVector3.at(30,70,-40);
        var source = new BlockArrayClipboard(new CuboidRegion(min,min.add(1,0,0)));
        source.setBlock(min,BlockTypes.STONE.getDefaultState());
        source.setBlock(min.add(1,0,0),BlockTypes.GOLD_BLOCK.getDefaultState());
        var repeated = new com.redawn.worldeditpp.bridge.worldedit.clipboard.RepeatedClipboard(source,BlockVector3.at(64,1,1));
        var larger = new com.redawn.worldeditpp.bridge.worldedit.clipboard.RepeatedClipboard(repeated,BlockVector3.at(128,1,1));
        helper.assertTrue(com.redawn.worldeditpp.bridge.worldedit.clipboard.RepeatedClipboard.template(larger) == source,"wrappers must not nest");
        helper.assertTrue(larger.getBlock(BlockVector3.at(127,0,0)).getBlockType() == BlockTypes.GOLD_BLOCK,"far tile modulo lookup");
        helper.assertTrue(larger.getBlock(BlockVector3.at(-1,0,0)).getBlockType() == BlockTypes.AIR,"negative neighbour is outside clipboard");
        helper.assertTrue(larger.getBlock(BlockVector3.at(128,0,0)).getBlockType() == BlockTypes.AIR,"positive neighbour is outside clipboard");
        helper.assertTrue(source.getDimensions().equals(BlockVector3.at(2,1,1)),"resize must not expand original storage");
        helper.succeed();
    }
}
