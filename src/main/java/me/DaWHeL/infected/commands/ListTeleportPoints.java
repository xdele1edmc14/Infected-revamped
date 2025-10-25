package me.DaWHeL.infected.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class ListTeleportPoints implements CommandExecutor {

    private final JavaPlugin plugin;

    public ListTeleportPoints(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().isConfigurationSection("teleports")) {
            sender.sendMessage(ChatColor.RED + "No teleport points found in config!");
            return true;
        }

        Set<String> points = plugin.getConfig().getConfigurationSection("teleports").getKeys(false);

        if (points.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No teleport points found!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Teleport Points:");
        for (String point : points) {
            String world = plugin.getConfig().getString("teleports." + point + ".world");
            double x = plugin.getConfig().getDouble("teleports." + point + ".x");
            double y = plugin.getConfig().getDouble("teleports." + point + ".y");
            double z = plugin.getConfig().getDouble("teleports." + point + ".z");

            sender.sendMessage(ChatColor.AQUA + point + ": " + ChatColor.GREEN + world +
                    " X:" + x + " Y:" + y + " Z:" + z);
        }

        return true;
    }
}
