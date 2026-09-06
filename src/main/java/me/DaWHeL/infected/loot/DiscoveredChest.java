package me.DaWHeL.infected.loot;

import org.bukkit.inventory.Inventory;

import java.util.Objects;

public record DiscoveredChest(String key, Inventory inventory) {
    public DiscoveredChest {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(inventory, "inventory");
    }
}
