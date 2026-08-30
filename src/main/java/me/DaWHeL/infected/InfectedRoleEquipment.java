package me.DaWHeL.infected;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.Supplier;

public final class InfectedRoleEquipment {
    private final NamespacedKey headKey;
    private final Supplier<ItemStack> headFactory;

    public InfectedRoleEquipment(Plugin plugin) {
        this(new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "infected_role_head"),
                () -> new ItemStack(Material.ZOMBIE_HEAD));
    }

    InfectedRoleEquipment(NamespacedKey headKey, Supplier<ItemStack> headFactory) {
        this.headKey = Objects.requireNonNull(headKey, "headKey");
        this.headFactory = Objects.requireNonNull(headFactory, "headFactory");
    }

    public ItemStack createHead() {
        ItemStack head = Objects.requireNonNull(headFactory.get(), "Zombie head factory result");
        ItemMeta meta = Objects.requireNonNull(head.getItemMeta(), "Zombie head metadata");
        meta.getPersistentDataContainer().set(headKey, PersistentDataType.BYTE, (byte) 1);
        head.setItemMeta(meta);
        return head;
    }

    public boolean isOwnedHead(ItemStack item) {
        return item != null
                && item.getType() == Material.ZOMBIE_HEAD
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(headKey, PersistentDataType.BYTE);
    }

    public boolean removeOwnedHead(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        if (!isOwnedHead(inventory.getHelmet())) {
            return false;
        }
        inventory.setHelmet(null);
        return true;
    }
}
