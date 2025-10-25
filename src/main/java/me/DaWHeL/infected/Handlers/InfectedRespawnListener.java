package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class InfectedRespawnListener implements Listener {

    private final GameManager gameManager;

    public InfectedRespawnListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // only if the game is running
        if (!gameManager.isGameRunning()) return;

        // check if the player is an infected zombie
        boolean isInfected = gameManager.getInfected().stream()
                .anyMatch(i -> i.getPlayer().equals(player));

        if (!isInfected) return;

        // get teleport points from config
        ConfigurationSection tpSection = gameManager.getPlugin().getConfig().getConfigurationSection("teleports");
        if (tpSection == null || tpSection.getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.RED + "No teleport points set! Respawning at world spawn...");
            return;
        }

        // pick a random valid teleport point inside the world border
        List<String> points = new ArrayList<>(tpSection.getKeys(false));
        Collections.shuffle(points); // randomize order

        Location chosenLoc = null;

        for (String point : points) {
            String worldName = tpSection.getString(point + ".world");
            double x = tpSection.getDouble(point + ".x");
            double y = tpSection.getDouble(point + ".y");
            double z = tpSection.getDouble(point + ".z");

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location loc = new Location(world, x, y + 1, z);
            WorldBorder border = world.getWorldBorder();

            // check if inside border
            if (border.isInside(loc)) {
                chosenLoc = loc;
                break;
            }
        }
        // fallback if no valid location found
        if (chosenLoc == null) {
            player.sendMessage(ChatColor.RED + "No valid teleport points inside the world border! Respawning at world spawn...");
            World world = player.getWorld();
            chosenLoc = world.getSpawnLocation().add(0, 1, 0);
        }

        event.setRespawnLocation(chosenLoc);
        player.sendMessage(ChatColor.GRAY + "You respawned as a zombie!");
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setHelmet(new ItemStack(Material.ZOMBIE_HEAD));

        Bukkit.getScheduler().runTaskLater(gameManager.getPlugin(), () -> {
            if (gameManager.isBuffEnabled()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false, true));
                player.getInventory().addItem(new ItemStack(Material.COMPASS));
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, true));
            }

        }, 10L);
    }
}
