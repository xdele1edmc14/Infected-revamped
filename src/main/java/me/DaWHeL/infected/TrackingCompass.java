package me.DaWHeL.infected;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

public final class TrackingCompass {
    private final Supplier<ItemStack> itemFactory;

    public TrackingCompass() {
        this(() -> new ItemStack(Material.COMPASS));
    }

    TrackingCompass(Supplier<ItemStack> itemFactory) {
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
    }

    public void ensurePresent(Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.getInventory().contains(Material.COMPASS)) {
            player.getInventory().addItem(itemFactory.get());
        }
    }

    public void remove(Player player) {
        Objects.requireNonNull(player, "player");
        player.getInventory().remove(Material.COMPASS);
    }
}
