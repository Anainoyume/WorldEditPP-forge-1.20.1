package dev.worldeditpp.testing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayContext;
import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionLayer;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ProjectionBlockEntityNbt;
import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;
import com.redawn.worldeditpp.network.SelectionBlockEntityData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/** Isolated real-client GPU audit, deliberately independent of input/network throughput. */
final class ProjectionRenderAudit {
    private static final boolean MIXED = Boolean.getBoolean("worldeditpp.test.mixed2600k");
    private static final SelectionOverlayState overlay = new SelectionOverlayState();
    private static SelectionBlockProjectionLayer layer;
    private static final List<Long> cpu = new ArrayList<>(), gpu = new ArrayList<>(), frames = new ArrayList<>();
    private static final int[] queries = new int[16];
    private static final boolean[] pending = new boolean[16];
    private static int scene, phase, warmup, slot, ticks;
    private static long started, previous, buildStarted;
    private static long drawStart, pageStart, beStart, fallbackStart;
    private static Path dir;
    private static CompletableFuture<?> teleport;
    private static boolean windowDone, expanded;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks > 10000) throw new AssertionError("Render audit timeout: " + scene + "/" + phase);
        var state = SelectionBlockProjectionController.state();
        if (phase == 0) {
            String label = System.getProperty("worldeditpp.test.renderAuditLabel", "audit");
            if (!label.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid audit label");
            dir = Path.of(System.getProperty("worldeditpp.test.report")).getParent().resolve("renderAudit").resolve(label);
            Files.createDirectories(dir);
            var querySwitch = Class.forName("com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.ProjectionOcclusionCuller").getDeclaredField("ENABLED");
            querySwitch.setAccessible(true);
            querySwitch.setBoolean(null, Boolean.parseBoolean(System.getProperty("worldeditpp.test.renderAuditQueries", "true")));
            Files.writeString(dir.resolve("samples.csv"), "scene,frames,fps,p95_ms,cpu_ms,gpu_ms,pages_per_frame,terrain_draws_per_frame,be_counter_per_frame,live_be_per_frame,heap_mb\n");
            Files.writeString(dir.resolve("environment.txt"), GL11.glGetString(GL11.GL_RENDERER) + "\n" + GL11.glGetString(GL11.GL_VERSION)
                    + "\nDiagnostic projection draws on cleared main target, not vanilla-world FPS.\nqueries="+querySwitch.getBoolean(null)+"\n");
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(260);
            mc.options.renderDistance().set(12); mc.options.fov().set(70);
            mc.options.hideGui = true;
            for (int i = 0; i < queries.length; i++) queries[i] = GL15.glGenQueries();
            teleport = mc.getSingleplayerServer().submit(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.teleportTo(mc.getSingleplayerServer().overworld(), 1015, 100, 970, 0, 15);
                p.getAbilities().flying = true; p.onUpdateAbilities();
            });
            phase = 1;
        } else if (phase == 1 && teleport.isDone()) {
            teleport.join();
            state.reset(); state.setActive(true);
            var snapshot = scene != 0 && MIXED ? MixedProjectionFixture.snapshot(mc) : snapshot(mc, scene != 0);
            var apply = state.getClass().getDeclaredMethod("applySnapshot", SelectionProjectionAssembler.SnapshotData.class);
            apply.setAccessible(true);
            if (!(Boolean) apply.invoke(state, snapshot)) throw new AssertionError("Snapshot rejected");
            expanded = false;
            int sx = state.sizeX(), sy = state.sizeY(), sz = state.sizeZ();
            overlay.replaceCuboidSelection(new CuboidSelectionPayload(true,1000,80,1000,999+sx,79+sy,999+sz,
                    1000,80,1000,999+sx,79+sy,999+sz));
            layer = new SelectionBlockProjectionLayer();
            buildStarted = System.nanoTime(); phase = 2;
        } else if (phase == 2 && state.isReady()) {
            if (scene != 0 && !expanded) {
                // resize grows both faces of each axis: 10 + 6 * 20 = 130.
                if (MIXED) {
                    for (int axis=0;axis<3;axis++) for (int i=0;i<(axis==0?25:9);i++)
                        if (!state.stretchFace(axis*2+1,true)) throw new AssertionError("Mixed stretch rejected");
                } else for (int i = 0; i < 6; i++) if (!state.resize(true)) throw new AssertionError("Resize rejected");
                expanded = true;
                overlay.replaceCuboidSelection(new CuboidSelectionPayload(true,1000,80,1000,999+state.sizeX(),79+state.sizeY(),999+state.sizeZ(),
                        1000,80,1000,999+state.sizeX(),79+state.sizeY(),999+state.sizeZ()));
                return false;
            }
            long indices = 0, entities = 0, emptyPages = 0;
            Map<?,?> pages = (Map<?,?>) field(field(state,"mesh"),"pages");
            for (Object mesh : pages.values()) {
                int count = ((List<?>)field(mesh,"blockEntities")).size(); entities += count;
                Map<?,?> vbos = (Map<?,?>)field(mesh,"blockEntityVbos");
                if (count > 0 && vbos.isEmpty()) emptyPages++;
                for (Object vbo : vbos.values()) indices += ((Number)field(vbo,"indexCount")).longValue();
            }
            Files.writeString(dir.resolve("cache-"+scene+".txt"), "volume="+(long)state.sizeX()*state.sizeY()*state.sizeZ()
                    +" entities="+entities+" entity_indices="+indices+" empty_entity_pages="+emptyPages
                    +" build_wall_ms="+(System.nanoTime()-buildStarted)/1e6+"\n");
            if (Boolean.getBoolean("worldeditpp.test.renderAuditStrict") && (emptyPages != 0 || indices == 0))
                throw new AssertionError("Missing BE cache at world x=1000: " + emptyPages);
            if (Boolean.getBoolean("worldeditpp.test.renderAuditStrict") && indices != entities * 108 / (MIXED && scene != 0 ? 2 : 1))
                throw new AssertionError("Incomplete chest geometry: " + indices + " for " + entities + " chests");
            warmup = 120; previous = 0; phase = 3;
        } else if (phase == 3 && windowDone) {
            layer.close(); state.reset(); layer = null; windowDone = false;
            if (++scene == 2) {
                phase = 4;
                for (int q : queries) GL15.glDeleteQueries(q);
                mc.options.hideGui = false;
                return true;
            }
            phase = 1;
        }
        if (phase >= 1) {
            double x = scene == 0 ? 1015 : 1065, y = scene == 0 ? 100 : 155, z = scene == 0 ? 970 : 930;
            mc.player.moveTo(x,y,z,0,scene == 0 ? 15 : 25);
            mc.player.setDeltaMovement(0,0,0); mc.player.getAbilities().flying = true;
        }
        return false;
    }

    static void render(RenderLevelStageEvent event) throws Exception {
        if (phase != 3 || windowDone) return;
        Minecraft mc = Minecraft.getInstance();
        if (Boolean.getBoolean("worldeditpp.test.auditHalfResolution")) {
            var target=mc.getMainRenderTarget();
            int width=mc.getWindow().getWidth()/2, height=mc.getWindow().getHeight()/2;
            if(target.width!=width || target.height!=height) target.resize(width,height,false);
            target.bindWrite(true);
        }
        clear();
        long now = System.nanoTime();
        boolean measuring = warmup == 0;
        if (measuring && previous != 0) frames.add(now-previous);
        previous = now;
        for (int i = 0; i < queries.length; i++) if (pending[i]
                && GL15.glGetQueryObjecti(queries[i],GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            long value = GL33.glGetQueryObjectui64(queries[i],GL15.GL_QUERY_RESULT);
            pending[i] = false;
            if (measuring) gpu.add(value);
        }
        boolean issued = !pending[slot];
        if (issued) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,queries[slot]);
        long begin = System.nanoTime();
        layer.render(new SelectionOverlayContext(mc,event.getCamera(),event.getPoseStack(),event.getProjectionMatrix()),overlay);
        long elapsed = System.nanoTime()-begin;
        if (issued) { GL15.glEndQuery(GL33.GL_TIME_ELAPSED); pending[slot] = true; }
        slot = (slot+1)%queries.length;
        if (measuring) cpu.add(elapsed);
        if (warmup > 0 && --warmup == 0) {
            cpu.clear(); gpu.clear(); frames.clear(); previous = 0; started = System.nanoTime();
            drawStart = SelectionBlockProjectionLayer.statDrawCalls;
            pageStart = SelectionBlockProjectionLayer.statVisiblePages;
            beStart = SelectionBlockProjectionLayer.statBlockEntityDraws;
            fallbackStart = SelectionBlockProjectionLayer.statVisibleBlockEntities;
        }
        if (measuring && frames.size() >= 120 && System.nanoTime()-started > 6_000_000_000L) {
            double n = cpu.size();
            Files.writeString(dir.resolve("samples.csv"),String.format(Locale.ROOT,
                    "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f%n",
                    scene == 0 ? "chests9000" : MIXED ? "mixed2600000" : "rooms2197000",frames.size(),1e9/mean(frames),percentile(frames,.95)/1e6,
                    mean(cpu)/1e6,mean(gpu)/1e6,(SelectionBlockProjectionLayer.statVisiblePages-pageStart)/n,
                    (SelectionBlockProjectionLayer.statDrawCalls-drawStart)/n,(SelectionBlockProjectionLayer.statBlockEntityDraws-beStart)/n,
                    (SelectionBlockProjectionLayer.statVisibleBlockEntities-fallbackStart)/n,
                    (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576.0),StandardOpenOption.APPEND);
            Files.write(dir.resolve("raw-"+scene+".csv"),frames.stream().map(Object::toString).toList());
            try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(dir.resolve("scene-"+scene+".png"));
                long colored=0;
                for(int y=0;y<pixels.getHeight();y++) for(int x=0;x<pixels.getWidth();x++) if((pixels.getPixelRGBA(x,y)&0xffffff)!=0) colored++;
                if(colored<1000) throw new AssertionError("Projection not visible: "+colored);
            }
            windowDone = true;
        }
    }

    private static SelectionProjectionAssembler.SnapshotData snapshot(Minecraft mc, boolean house) {
        int sx=house?10:30, sy=house?10:10, sz=house?10:30;
        var palette=List.of(Blocks.CHEST.defaultBlockState(),Blocks.BRICKS.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),Blocks.OAK_STAIRS.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState());
        List<Integer> entries=new ArrayList<>(); List<SelectionBlockEntityData> nbt=new ArrayList<>();
        for(int z=0;z<sz;z++) for(int y=0;y<sy;y++) for(int x=0;x<sx;x++) {
            int id=0;
            if(house) {
                id=-1;
                if(y==0||y==9) id=2;
                else if(x==0||z==0||x==9||z==9) id=(y>=3&&y<=5)?3:1;
                else if(y==1&&x==2&&z==2) id=0;
                else if(y==1&&x==3&&z==3) id=4;
                else if(y==1&&x==4&&z==3) id=5;
                if(id<0) continue;
            }
            int cell=x+sx*(y+sy*z); entries.add(cell); entries.add(id);
            if(id==0) {
                var chest=new ChestBlockEntity(new BlockPos(x,y,z),palette.get(0));
                ItemStack stack=new ItemStack(Items.DIAMOND,17); stack.getOrCreateTag().putInt("auditCell",cell);
                chest.setItem(0,stack);
                nbt.add(ProjectionBlockEntityNbt.capture(cell,chest,mc.level.registryAccess()));
            }
        }
        return new SelectionProjectionAssembler.SnapshotData(123,1000,80,1000,sx,sy,sz,
                palette.stream().mapToInt(Block::getId).toArray(),entries.stream().mapToInt(Integer::intValue).toArray(),nbt);
    }
    private static Object field(Object owner,String name) throws Exception {
        var f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static double mean(List<Long> values) { return values.stream().mapToLong(Long::longValue).average().orElse(Double.NaN); }
    private static long percentile(List<Long> values,double p) { var a=values.stream().mapToLong(Long::longValue).sorted().toArray(); return a[(int)((a.length-1)*p)]; }
    private static void clear() {
        RenderSystem.colorMask(true,true,true,true); RenderSystem.depthMask(true);
        RenderSystem.clearColor(0,0,0,1); RenderSystem.clearDepth(1); RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
    }
}
