package me.DaWHeL.infected.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

final class AdminGuiItems {
    private AdminGuiItems() {
    }

    static ItemStack item(Material material, ChatColor color, String name, String... lore) {
        return item(material, color + name, Arrays.asList(lore));
    }

    static ItemStack item(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack playerHead(Player player, ChatColor color, String team) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(color + player.getName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Player: " + ChatColor.WHITE + player.getName(),
                ChatColor.GRAY + "Team: " + color + team,
                "",
                ChatColor.AQUA + "Click: " + ChatColor.GRAY + "View actions"
        ));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack background(Material material) {
        return item(material, ChatColor.DARK_GRAY + " ", List.of());
    }
}
