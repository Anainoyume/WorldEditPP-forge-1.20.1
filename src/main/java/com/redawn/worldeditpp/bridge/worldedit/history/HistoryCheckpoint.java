package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.history.change.*;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BaseBlock;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Streaming shutdown checkpoint. Never claims to be a write-ahead log for process crashes. */
final class HistoryCheckpoint {
    private static final int MAGIC_V1 = 0x57505031;
    private static final int MAGIC_V2 = 0x57505032;
    private static final int MAX_RECORDS = 64 * 1024 * 1024;

    static Path path(MinecraftServer server, UUID owner) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/worldeditpp-history").resolve(owner + ".pending");
    }

    static void write(MinecraftServer server, UUID owner, ServerLevel level, List<Change> changes,
                      int count, Change uncertain, boolean undo, Map<Change, CompoundTag> entitySnapshots) throws IOException {
        if (count == 0 && uncertain == null) return;
        if (count < 0 || count > changes.size()) throw new IOException("Invalid checkpoint prefix");
        int records = Math.addExact(count, uncertain == null ? 0 : 1);
        Path path = path(server, owner), temporary = path.resolveSibling(owner + ".tmp");
        Files.createDirectories(path.getParent());
        try {
            try (var out = new DataOutputStream(new FastGzipOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(MAGIC_V2);
                out.writeUTF(level.dimension().location().toString());
                out.writeInt(records);
                Writer writer = new Writer(out, undo, entitySnapshots);
                // Recovery visits the applied prefix in reverse order.
                if (uncertain != null) writer.write(uncertain);
                for (int i = count - 1; i >= 0; i--) writer.write(changes.get(i));
            }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    static void complete(MinecraftServer server, UUID owner) throws IOException {
        Path path = path(server, owner);
        Files.move(path, path.resolveSibling(owner + ".recovered"), StandardCopyOption.REPLACE_EXISTING);
    }

    static final class Reader implements AutoCloseable {
        final DataInputStream input;
        final ServerLevel level;
        private final boolean compact;
        private final List<BaseBlock> blocks = new ArrayList<>();
        private final List<String> biomes = new ArrayList<>();
        private int remaining;
        private int x, y, z;

        Reader(MinecraftServer server, UUID owner) throws IOException {
            input = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(path(server, owner)), 64 * 1024), 64 * 1024));
            try {
                int magic = input.readInt();
                if (magic != MAGIC_V1 && magic != MAGIC_V2) throw new IOException("Unknown history checkpoint format");
                compact = magic == MAGIC_V2;
                level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(input.readUTF())));
                if (level == null) throw new IOException("Checkpoint dimension is not available");
                remaining = input.readInt();
                if (remaining < 0 || remaining > MAX_RECORDS) throw new IOException("Invalid checkpoint size");
            } catch (Exception e) {
                input.close();
                throw e;
            }
        }

        boolean hasNext() { return remaining > 0; }

        Change next() throws IOException {
            if (remaining <= 0) throw new EOFException("No checkpoint records remain");
            Change result = compact ? nextCompact() : nextLegacy();
            remaining--;
            return result;
        }

        private Change nextLegacy() throws IOException {
            CompoundTag tag = readNbt(input);
            var pos = BlockVector3.at(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            return switch (tag.getString("type")) {
                case "block" -> new BlockChange(pos, block(tag.getCompound("before")), block(tag.getCompound("after")));
                case "biome" -> new BiomeChange3D(pos,
                        requiredBiome(tag.getString("before")), requiredBiome(tag.getString("after")));
                case "entity" -> new RecoveryEntityChange(level, tag.getCompound("entity"), tag.getBoolean("afterPresent"), pos);
                default -> throw new IOException("Unsupported checkpoint record");
            };
        }

        private Change nextCompact() throws IOException {
            int type = input.readUnsignedByte();
            x = Math.addExact(x, readSignedVarInt(input));
            y = Math.addExact(y, readSignedVarInt(input));
            z = Math.addExact(z, readSignedVarInt(input));
            BlockVector3 pos = BlockVector3.at(x, y, z);
            return switch (type) {
                case 0 -> new BlockChange(pos, readBlockRef(input, blocks), readBlockRef(input, blocks));
                case 1 -> new BiomeChange3D(pos, requiredBiome(readStringRef(input, biomes)),
                        requiredBiome(readStringRef(input, biomes)));
                case 2 -> new RecoveryEntityChange(level, readNbt(input), input.readBoolean(), pos);
                default -> throw new IOException("Unsupported compact checkpoint record: " + type);
            };
        }

        @Override public void close() throws IOException { input.close(); }
    }

    private static final class Writer {
        private final DataOutputStream output;
        private final boolean undo;
        private final Map<Change, CompoundTag> entitySnapshots;
        private final Map<BaseBlock, Integer> blocks = new HashMap<>();
        private final Map<String, Integer> biomes = new HashMap<>();
        private int x, y, z;

        Writer(DataOutputStream output, boolean undo, Map<Change, CompoundTag> entitySnapshots) {
            this.output = output;
            this.undo = undo;
            this.entitySnapshots = entitySnapshots;
        }

        void write(Change original) throws IOException {
            Change change = original;
            boolean direction = undo;
            while (change instanceof HistoryChanges.ReversedChange reverse) {
                change = reverse.original();
                direction = !direction;
            }
            BlockVector3 position = HistoryChanges.position(change);
            if (change instanceof BlockChange block) {
                header(0, position);
                writeBlockRef(output, direction ? block.getCurrent() : block.getPrevious(), blocks);
                writeBlockRef(output, direction ? block.getPrevious() : block.getCurrent(), blocks);
            } else if (change instanceof BiomeChange3D biome) {
                header(1, position);
                writeStringRef(output, (direction ? biome.getCurrent() : biome.getPrevious()).getId(), biomes);
                writeStringRef(output, (direction ? biome.getPrevious() : biome.getCurrent()).getId(), biomes);
            } else if (change instanceof BiomeChange biome) {
                header(1, position);
                writeStringRef(output, (direction ? biome.getCurrent() : biome.getPrevious()).getId(), biomes);
                writeStringRef(output, (direction ? biome.getPrevious() : biome.getCurrent()).getId(), biomes);
            } else {
                header(2, position);
                EntityRecord entity = entity(change, direction, entitySnapshots.get(original));
                NbtIo.write(entity.data, output);
                output.writeBoolean(entity.afterPresent);
            }
        }

        private void header(int type, BlockVector3 position) throws IOException {
            output.writeByte(type);
            writeSignedVarInt(output, position.getX() - x);
            writeSignedVarInt(output, position.getY() - y);
            writeSignedVarInt(output, position.getZ() - z);
            x = position.getX(); y = position.getY(); z = position.getZ();
        }
    }

    private record EntityRecord(CompoundTag data, boolean afterPresent) {}

    private static EntityRecord entity(Change change, boolean undo, CompoundTag snapshot) {
        if (change instanceof RecoveryEntityChange entity) {
            return new EntityRecord(entity.data, entity.afterPresent != undo);
        }
        var type = change.getClass();
        var live = (com.sk89q.worldedit.entity.Entity) HistorySessionAccess.read(
                HistorySessionAccess.field(type, "entity"), change);
        var state = live == null
                ? (BaseEntity) HistorySessionAccess.read(HistorySessionAccess.field(type, "state"), change)
                : live.getState();
        if (snapshot == null && (state == null || state.getNbtData() == null)) {
            throw new IllegalStateException("Missing checkpoint entity NBT");
        }
        CompoundTag data = snapshot != null ? snapshot : WorldEditPlatform.toNative(state.getNbtData());
        if (snapshot == null) data.putString("id", state.getType().getId());
        if (!data.hasUUID("UUID")) throw new IllegalStateException("Missing checkpoint entity UUID");
        return new EntityRecord(data, (change instanceof EntityCreate) != undo);
    }

    private static CompoundTag block(BaseBlock block) {
        var tag = new CompoundTag();
        tag.put("state", NbtUtils.writeBlockState(WorldEditPlatform.adapt(block.toImmutableState())));
        if (block.getNbtData() != null) tag.put("nbt", WorldEditPlatform.toNative(block.getNbtData()));
        return tag;
    }

    private static BaseBlock block(CompoundTag tag) {
        var state = WorldEditPlatform.adapt(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("state")));
        return tag.contains("nbt") ? state.toBaseBlock(WorldEditPlatform.fromNative(tag.getCompound("nbt"))) : state.toBaseBlock();
    }

    private static void writeBlockRef(DataOutput out, BaseBlock block, Map<BaseBlock, Integer> palette) throws IOException {
        Integer id = palette.get(block);
        if (id == null) {
            id = palette.size();
            palette.put(block, id);
            writeVarInt(out, (id << 1) | 1);
            NbtIo.write(block(block), out);
        } else {
            writeVarInt(out, id << 1);
        }
    }

    private static BaseBlock readBlockRef(DataInput in, List<BaseBlock> palette) throws IOException {
        int token = readVarInt(in);
        if (token < 0) throw new IOException("Invalid block palette token");
        int id = token >>> 1;
        if ((token & 1) != 0) {
            if (id != palette.size()) throw new IOException("Out-of-order block palette entry");
            palette.add(block(readNbt(in)));
        }
        if (id >= palette.size()) throw new IOException("Unknown block palette entry");
        return palette.get(id);
    }

    private static void writeStringRef(DataOutput out, String value, Map<String, Integer> palette) throws IOException {
        Integer id = palette.get(value);
        if (id == null) {
            id = palette.size();
            palette.put(value, id);
            writeVarInt(out, (id << 1) | 1);
            out.writeUTF(value);
        } else {
            writeVarInt(out, id << 1);
        }
    }

    private static String readStringRef(DataInput in, List<String> palette) throws IOException {
        int token = readVarInt(in);
        if (token < 0) throw new IOException("Invalid string palette token");
        int id = token >>> 1;
        if ((token & 1) != 0) {
            if (id != palette.size()) throw new IOException("Out-of-order string palette entry");
            palette.add(in.readUTF());
        }
        if (id >= palette.size()) throw new IOException("Unknown string palette entry");
        return palette.get(id);
    }

    private static com.sk89q.worldedit.world.biome.BiomeType requiredBiome(String id) throws IOException {
        var biome = BiomeTypes.get(id);
        if (biome == null) throw new IOException("Unknown checkpoint biome: " + id);
        return biome;
    }

    private static CompoundTag readNbt(DataInput input) throws IOException {
        CompoundTag tag = NbtIo.read(input, new NbtAccounter(32L * 1024 * 1024));
        if (tag == null) throw new IOException("Missing checkpoint NBT");
        return tag;
    }

    private static void writeSignedVarInt(DataOutput out, int value) throws IOException {
        writeVarInt(out, (value << 1) ^ (value >> 31));
    }

    private static int readSignedVarInt(DataInput in) throws IOException {
        int value = readVarInt(in);
        return (value >>> 1) ^ -(value & 1);
    }

    private static void writeVarInt(DataOutput out, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            out.writeByte((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInput in) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int value = in.readUnsignedByte();
            result |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) return result;
        }
        throw new IOException("Malformed checkpoint varint");
    }

    private static final class FastGzipOutputStream extends GZIPOutputStream {
        FastGzipOutputStream(OutputStream output) throws IOException {
            super(new BufferedOutputStream(output, 64 * 1024), 64 * 1024);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }

    private HistoryCheckpoint() {}
}
