package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Objects;

public final class ItemSnapshotCodec {
    public String encode(ItemStack item) {
        requireItem(item);
        return Base64.getEncoder().encodeToString(item.clone().serializeAsBytes());
    }

    public ItemStack decode(String payload) {
        Objects.requireNonNull(payload, "payload");
        ItemStack decoded = ItemStack.deserializeBytes(Base64.getDecoder().decode(payload));
        requireItem(decoded);
        return decoded.clone();
    }

    private static void requireItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            throw new IllegalArgumentException("An actual item is required.");
        }
    }
}
