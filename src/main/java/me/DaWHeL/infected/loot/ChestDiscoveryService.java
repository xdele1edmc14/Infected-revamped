package me.DaWHeL.infected.loot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChestDiscoveryService {
    private final Function<String, World> worldLookup;

    public ChestDiscoveryService() {
        this(Bukkit::getWorld);
    }

    ChestDiscoveryService(Function<String, World> worldLookup) {
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
    }

    public DiscoveryResult discover(ChestRegion region) {
        World world = worldLookup.apply(region.world());
        if (world == null) return DiscoveryResult.failure("Selected world is not loaded: " + region.world());
        Set<ChestRegion.ChunkKey> chunkKeys = region.chunkKeys();
        long unloaded = chunkKeys.stream()
                .filter(key -> !world.isChunkLoaded(key.x(), key.z())).count();
        if (unloaded > 0) {
            return DiscoveryResult.failure(unloaded + " selected chunk(s) are not loaded.");
        }
        Map<String, DiscoveredChest> found = new LinkedHashMap<>();
        for (ChestRegion.ChunkKey chunkKey : chunkKeys) {
            discover(region, world.getChunkAt(chunkKey.x(), chunkKey.z())).forEach(
                    chest -> found.putIfAbsent(chest.key(), chest));
        }
        List<DiscoveredChest> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparing(DiscoveredChest::key));
        return DiscoveryResult.success(result);
    }

    List<DiscoveredChest> discover(ChestRegion region, org.bukkit.Chunk chunk) {
        Map<String, DiscoveredChest> found = new LinkedHashMap<>();
        Predicate<Block> chestInRegion = block -> isChest(block.getType())
                && region.contains(block.getX(), block.getY(), block.getZ());
        for (BlockState state : chunk.getTileEntities(chestInRegion, false)) {
            if (!(state instanceof Chest chest)) continue;
            Inventory inventory = inventoryInsideRegion(region, chest);
            String key = canonicalKey(chest, inventory);
            found.putIfAbsent(key, new DiscoveredChest(key, inventory));
        }
        List<DiscoveredChest> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparing(DiscoveredChest::key));
        return result;
    }

    private static Inventory inventoryInsideRegion(ChestRegion region, Chest chest) {
        Inventory inventory = chest.getInventory();
        if (!(inventory.getHolder() instanceof DoubleChest doubleChest)) return inventory;
        if (holderInside(region, doubleChest.getLeftSide())
                && holderInside(region, doubleChest.getRightSide())) return inventory;
        return chest.getBlockInventory();
    }

    private static boolean holderInside(ChestRegion region, InventoryHolder holder) {
        Location location = holder instanceof BlockState state ? state.getLocation()
                : holder instanceof DoubleChest chest ? chest.getLocation() : null;
        return location != null && region.contains(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    World world(String name) {
        return worldLookup.apply(name);
    }

    private static boolean isChest(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    private static String canonicalKey(Chest chest, Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            String left = holderKey(doubleChest.getLeftSide());
            String right = holderKey(doubleChest.getRightSide());
            return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
        }
        return locationKey(chest.getLocation());
    }

    private static String holderKey(InventoryHolder holder) {
        if (holder instanceof BlockState state) return locationKey(state.getLocation());
        if (holder instanceof DoubleChest chest) return locationKey(chest.getLocation());
        return holder.getClass().getName() + '@' + System.identityHashCode(holder);
    }

    private static String locationKey(Location location) {
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + ':' + location.getBlockX() + ':' + location.getBlockY() + ':' + location.getBlockZ();
    }

    public record DiscoveryResult(List<DiscoveredChest> chests, List<String> errors) {
        public DiscoveryResult {
            chests = List.copyOf(chests);
            errors = List.copyOf(errors);
        }
        public static DiscoveryResult success(List<DiscoveredChest> chests) { return new DiscoveryResult(chests, List.of()); }
        public static DiscoveryResult failure(String error) { return new DiscoveryResult(List.of(), List.of(error)); }
        public boolean success() { return errors.isEmpty(); }
    }
}
