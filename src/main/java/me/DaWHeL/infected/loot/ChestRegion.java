package me.DaWHeL.infected.loot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record ChestRegion(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public ChestRegion {
        Objects.requireNonNull(world, "world");
        if (world.isBlank() || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid chest region.");
        }
    }

    public static ChestRegion between(BlockPoint first, BlockPoint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.world().equals(second.world())) {
            throw new IllegalArgumentException("Both selection points must be in the same world.");
        }
        return new ChestRegion(first.world(),
                Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volume() {
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        return Math.multiplyExact(Math.multiplyExact(width, height), depth);
    }

    public Set<ChunkKey> chunkKeys() {
        return chunkKeys(Integer.MAX_VALUE);
    }

    public Set<ChunkKey> chunkKeys(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Maximum chunk count must be positive.");
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        long count = chunkCount();
        if (count > maximum) {
            throw new IllegalArgumentException("Selected region crosses too many chunks (" + count
                    + "; maximum " + maximum + ").");
        }
        Set<ChunkKey> keys = new LinkedHashSet<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                keys.add(new ChunkKey(x, z));
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    public long chunkCount() {
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        return Math.multiplyExact((long) maxChunkX - minChunkX + 1L,
                (long) maxChunkZ - minChunkZ + 1L);
    }

    public record ChunkKey(int x, int z) {
    }
}
