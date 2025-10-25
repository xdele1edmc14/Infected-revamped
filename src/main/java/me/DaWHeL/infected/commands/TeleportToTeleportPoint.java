package me.DaWHeL.infected.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TeleportToTeleportPoint implements CommandExecutor {
    private final JavaPlugin plugin;

    public TeleportToTeleportPoint(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tttp <name>");
            return true;
        }

        String pointName = args[0];
        String basePath = "teleports." + pointName;

        if (!plugin.getConfig().contains(basePath)) {
            player.sendMessage(ChatColor.RED + "Teleport point '" + pointName + "' does not exist!");
            return true;
        }

        String worldName = plugin.getConfig().getString(basePath + ".world");
        double x = plugin.getConfig().getDouble(basePath + ".x");
        double y = plugin.getConfig().getDouble(basePath + ".y");
        double z = plugin.getConfig().getDouble(basePath + ".z");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World '" + worldName + "' is not loaded!");
            return true;
        }

        Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
        player.teleport(loc);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + pointName + "!");
        return true;
    }
}
