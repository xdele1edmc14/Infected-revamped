package me.DaWHeL.infected.loot;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

public final class WeaponSelectionListener implements Listener {
    private final WeaponLootRepository repository;
    private final NamespacedKey wandKey;

    public WeaponSelectionListener(Plugin plugin, WeaponLootRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.wandKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "weapon_region_wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Weapon Chest Selection Wand");
        meta.setLore(List.of(
                ChatColor.YELLOW + "Left-click a block: " + ChatColor.GRAY + "Set point 1",
                ChatColor.YELLOW + "Right-click a block: " + ChatColor.GRAY + "Set point 2"
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean giveWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) {
                player.sendMessage(ChatColor.YELLOW + "You already have the weapon chest selection wand.");
                return true;
            }
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            player.sendMessage(ChatColor.RED + "Your inventory is full. Free one slot for the selection wand.");
            return false;
        }
        player.getInventory().setItem(slot, createWand());
        player.sendMessage(ChatColor.GREEN + "Weapon chest selection wand added to your inventory.");
        return true;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onSelect(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!isWand(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("infected.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to select weapon chest regions.");
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) return;
        int point = action == Action.LEFT_CLICK_BLOCK ? 1 : 2;
        BlockPoint selected = new BlockPoint(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        WeaponLootCatalog before = repository.snapshot();
        BlockPoint other = point == 1 ? before.point2() : before.point1();
        repository.setPoint(point, selected);
        if (other != null && !other.world().equals(selected.world())) {
            player.sendMessage(ChatColor.YELLOW + "The other point was cleared because it was in a different world.");
        }
        player.sendMessage(ChatColor.GREEN + "Weapon chest point " + point + " set to "
                + selected.x() + ", " + selected.y() + ", " + selected.z() + " in " + selected.world() + ".");
    }
}
