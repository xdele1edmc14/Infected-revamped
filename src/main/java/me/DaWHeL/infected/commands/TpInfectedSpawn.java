package me.DaWHeL.infected.commands;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TpInfectedSpawn implements CommandExecutor {

    private final JavaPlugin plugin;

    public TpInfectedSpawn(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        FileConfiguration config = plugin.getConfig();

        // Check if spawn is set
        if (!config.isConfigurationSection("infected-spawn")) {
            player.sendMessage(ChatColor.RED + "No infected spawn has been set! Use /createinfectedspawn first.");
            return true;
        }

        String worldName = config.getString("infected-spawn.world");
        double x = config.getDouble("infected-spawn.x");
        double y = config.getDouble("infected-spawn.y");
        double z = config.getDouble("infected-spawn.z");
        float yaw = (float) config.getDouble("infected-spawn.yaw");
        float pitch = (float) config.getDouble("infected-spawn.pitch");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "The world '" + worldName + "' does not exist!");
            return true;
        }

        Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
        player.teleport(spawnLoc);
        player.sendMessage(ChatColor.GREEN + "Teleported to infected spawn!");

        return true;
    }
}
