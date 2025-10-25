package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateInfectedSpawn implements CommandExecutor {

    private final InfectedPlugin plugin;

    public CreateInfectedSpawn(InfectedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Location loc = player.getLocation();
        plugin.getConfig().set("infected-spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("infected-spawn.x", loc.getX());
        plugin.getConfig().set("infected-spawn.y", loc.getY());
        plugin.getConfig().set("infected-spawn.z", loc.getZ());
        plugin.getConfig().set("infected-spawn.yaw", loc.getYaw());
        plugin.getConfig().set("infected-spawn.pitch", loc.getPitch());
        plugin.saveConfig();

        player.sendMessage(ChatColor.GREEN + "Infected spawn set at your current location!");
        return true;
    }
}
