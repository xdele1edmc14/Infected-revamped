package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class JumpFeatherListener implements Listener {

    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final Set<Player> noFallPlayers = new HashSet<>();

    public JumpFeatherListener(GameManager gameManager, JavaPlugin plugin) {
        this.gameManager = gameManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onFeatherUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!gameManager.isGameRunning()) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FEATHER) return;

        // Check cooldown
        if (gameManager.isOnFeatherCooldown(player)) {
            player.sendMessage(ChatColor.RED + "You must wait before using another Jump Feather!");
            event.setCancelled(true);
            return;
        }

        // Launch player
        player.setVelocity(player.getVelocity().setY(2.5));

        // Optional: add visual/sound effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);

        // Remove feather
        player.getInventory().removeItem(item);

        // Set cooldown (15 seconds)
        gameManager.setFeatherCooldown(player, 15);

        noFallPlayers.add(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                noFallPlayers.remove(player);
            }
        }.runTaskLater(plugin, 20L * 5); // 5 seconds of no fall damage

        player.sendMessage(ChatColor.GREEN + "You used the Jump Feather!");
        event.setCancelled(true);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && noFallPlayers.contains(player)) {
            event.setCancelled(true);
        }
    }

}
