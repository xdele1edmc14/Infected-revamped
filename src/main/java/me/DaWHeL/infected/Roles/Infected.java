package me.DaWHeL.infected.Roles;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Infected {
    private final Player player;
    private final JavaPlugin plugin;

    public Infected(JavaPlugin plugin, Player player, boolean announce) {
        this.player = player;
        this.plugin = plugin;
        setup(announce);
    }

    public void scareEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); // 60 ticks = 3 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 1.0f);
    }

    private void setup(boolean announce) {
        player.setGlowing(true);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setHelmet(new ItemStack(Material.ZOMBIE_HEAD));
        player.setPlayerListName(ChatColor.RED + player.getName());
        player.sendMessage(ChatColor.RED + "You are now a zombie!");
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, true));

        scareEffect(player);

        if (announce) {
            Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " has been infected!");
        }
    }

    public Player getPlayer() {
        return player;
    }
}
