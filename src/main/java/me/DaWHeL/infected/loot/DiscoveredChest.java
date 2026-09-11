package me.DaWHeL.infected.loot;

import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Set;

public record DiscoveredChest(String key, Inventory inventory, Set<ChestRegion.ChunkKey> chunks) {
    public DiscoveredChest(String key, Inventory inventory) {
        this(key, inventory, Set.of());
    }

    public DiscoveredChest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(inventory, "inventory");
        chunks = Set.copyOf(chunks);
    }
}
