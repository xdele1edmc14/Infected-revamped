package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

public final class ChestEligibility {
    private final NamespacedKey markerKey;

    public ChestEligibility(NamespacedKey markerKey) {
        this.markerKey = Objects.requireNonNull(markerKey, "markerKey");
    }

    public boolean isEligible(Chest chest) {
        Objects.requireNonNull(chest, "chest");
        if (chest.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (chest.getBlock().getRelative(dx, -1, dz).getType() != Material.GOLD_BLOCK) return false;
            }
        }
        return true;
    }
}
