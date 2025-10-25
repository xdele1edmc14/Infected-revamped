package me.DaWHeL.infected.commands;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GiveFeather implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command!");
            return true;
        }

        // Create the Jump Feather item
        ItemStack feather = new ItemStack(Material.FEATHER);
        ItemMeta meta = feather.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Jump Feather");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Use this if you're stuck!");
        lore.add(ChatColor.YELLOW + "Launches you high into the air.");
        meta.setLore(lore);

        feather.setItemMeta(meta);

        // Add it to the player's inventory
        player.getInventory().addItem(feather);
        player.sendMessage(ChatColor.GREEN + "You received a Jump Feather!");

        return true;
    }
}
