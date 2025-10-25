package me.DaWHeL.infected;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TeleportManager {

    private final InfectedPlugin plugin;

    public TeleportManager(InfectedPlugin plugin) {
        this.plugin = plugin;
    }

    // Add teleport point
    public void addTeleportPoint(Player player, String name) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Build flat 5x5 gold platform with iron center
        int half = 2; // 5x5 = radius 2
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Material blockType = (x == 0 && z == 0) ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
                world.getBlockAt(loc.getBlockX() + x, loc.getBlockY(), loc.getBlockZ() + z).setType(blockType);
            }
        }

        // Save center coordinates to config
        plugin.getConfig().set("teleports." + name + ".world", world.getName());
        plugin.getConfig().set("teleports." + name + ".x", loc.getBlockX());
        plugin.getConfig().set("teleports." + name + ".y", loc.getBlockY());
        plugin.getConfig().set("teleports." + name + ".z", loc.getBlockZ());
        plugin.saveConfig();

        player.sendMessage("Teleport point " + name + " added!");
    }

    // Remove teleport point
    public void removeTeleportPoint(String name) {
        if (!plugin.getConfig().contains("teleports." + name)) {
            return;
        }

        // Get location
        String path = "teleports." + name;
        World world = Bukkit.getWorld(plugin.getConfig().getString(path + ".world"));
        int x = plugin.getConfig().getInt(path + ".x");
        int y = plugin.getConfig().getInt(path + ".y");
        int z = plugin.getConfig().getInt(path + ".z");

        // Remove platform (5x5)
        int half = 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.AIR);
            }
        }

        // Remove from config
        plugin.getConfig().set("teleports." + name, null);
        plugin.saveConfig();
    }

    // Get all teleport points as Locations
    public List<Location> getTeleportPoints() {
        List<Location> points = new ArrayList<>();
        if (!plugin.getConfig().contains("teleports")) return points;

        for (String key : plugin.getConfig().getConfigurationSection("teleports").getKeys(false)) {
            String worldName = plugin.getConfig().getString("teleports." + key + ".world");
            World world = Bukkit.getWorld(worldName);
            int x = plugin.getConfig().getInt("teleports." + key + ".x");
            int y = plugin.getConfig().getInt("teleports." + key + ".y");
            int z = plugin.getConfig().getInt("teleports." + key + ".z");
            if (world != null) points.add(new Location(world, x, y, z));
        }
        return points;
    }

    // Teleport a list of players in batches with delay
    public void teleportPlayersBatch(List<Player> players, int batchSize, int delayTicks, Runnable finishAction) {
        List<Player> queue = new ArrayList<>(players);
        List<Location> platforms = getTeleportPoints();
        if (platforms.isEmpty()) return;

        new BukkitRunnable() {
            int index = 0;
            @Override
            public void run() {
                int count = 0;
                while (count < batchSize && index < queue.size()) {
                    Player p = queue.get(index);
                    // Calculate smart distribution
                    Location platform = platforms.get(index % platforms.size());
                    Location tpLoc = getNextSlot(platform, index / platforms.size());
                    p.teleport(tpLoc);
                    index++;
                    count++;
                }
                if (index >= queue.size()) {
                    cancel();
                    if (finishAction != null) finishAction.run();
                }
            }
        }.runTaskTimer(plugin, 0L, delayTicks);
    }

    // Get next slot inside 5x5 platform
    private Location getNextSlot(Location center, int slotIndex) {
        int half = 2; // 5x5
        int row = slotIndex / 5;
        int col = slotIndex % 5;
        double x = center.getX() - half + col;
        double y = center.getY();
        double z = center.getZ() - half + row;
        return new Location(center.getWorld(), x + 0.5, y, z + 0.5); // +0.5 to center in block
    }
}
