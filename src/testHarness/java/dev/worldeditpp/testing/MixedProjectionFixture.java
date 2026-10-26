package dev.worldeditpp.testing;

import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** A fully occupied 10-cube: each repetition includes one populated chest and barrel. */
final class MixedProjectionFixture {
    static SelectionProjectionAssembler.SnapshotData snapshot(Minecraft mc) {
        var palette = new ArrayList<BlockState>();
        palette.add(Blocks.CHEST.defaultBlockState());
        palette.add(Blocks.BARREL.defaultBlockState());
        for (Block block : new Block[]{Blocks.STONE,Blocks.BRICKS,Blocks.COBBLESTONE,Blocks.OAK_PLANKS,
                Blocks.DEEPSLATE,Blocks.WHITE_CONCRETE}) palette.add(block.defaultBlockState());
        for (Direction.Axis axis : Direction.Axis.values())
            palette.add(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis));
        for (Direction facing : Direction.Plane.HORIZONTAL) for (Half half : Half.values())
            palette.add(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,half));
        for (SlabType type : SlabType.values())
            palette.add(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,type));
        int[] entries=new int[2000];
        var nbt=new ArrayList<SelectionBlockEntityData>();
        for(int z=0;z<10;z++) for(int y=0;y<10;y++) for(int x=0;x<10;x++) {
            int cell=x+10*(y+10*z);
            int id=y==9 ? 8+(x+10*z)%(palette.size()-8) : 2+(x+y+z)%6;
            if(y==1 && z==2 && (x==2||x==3)) id=x-2;
            entries[2*cell]=cell; entries[2*cell+1]=id;
            if(id<2) {
                var pos=new BlockPos(x,y,z);
                RandomizableContainerBlockEntity entity=id==0 ? new ChestBlockEntity(pos,palette.get(id))
                        : new BarrelBlockEntity(pos,palette.get(id));
                var stack=new ItemStack(id==0?Items.DIAMOND:Items.EMERALD,17);
                stack.getOrCreateTag().putString("fixture","mixed2600k-"+id);
                entity.setItem(0,stack);
                nbt.add(ProjectionBlockEntityNbt.capture(cell,entity,mc.level.registryAccess()));
            }
        }
        return new SelectionProjectionAssembler.SnapshotData(2600,1000,80,1000,10,10,10,
                palette.stream().mapToInt(Block::getId).toArray(),entries,nbt);
    }
}
