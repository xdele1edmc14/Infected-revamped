package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class BuffInfectedCommand implements CommandExecutor {

    private final GameManager gameManager;
    private final InfectedPlugin plugin;

    public BuffInfectedCommand(GameManager gameManager, InfectedPlugin plugin) {
        this.gameManager = gameManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command!");
            return true;
        }

        if (!gameManager.isGameRunning()) {
            sender.sendMessage(ChatColor.RED + "The game is not running!");
            return true;
        }
        // Toggle buff mode
        boolean newState = !gameManager.isBuffEnabled();
        gameManager.setBuffEnabled(newState);

        if (!newState) {
            sender.sendMessage(ChatColor.RED + "Infected buffs disabled!");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Infected buffs enabled! Buffs applied to all infected.");

        List<Infected> infectedList = gameManager.getInfected();

        if (infectedList.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "There are no infected players!");
            return true;
        }

        // Apply buffs to all infected players
        for (Infected infected : infectedList) {
            Player infectedPlayer = infected.getPlayer();

            infectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true));
            infectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false, true));
            infectedPlayer.getInventory().addItem(new ItemStack(Material.COMPASS));
        }

        // Schedule repeating compass updates
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isGameRunning() || !gameManager.isBuffEnabled()) {
                    cancel();
                    return;
                }

                List<Survivor> survivors = gameManager.getSurvivors();

                for (Infected infected : infectedList) {
                    Player infectedPlayer = infected.getPlayer();
                    Player nearest = getNearestSurvivor(infectedPlayer, survivors);
                    if (nearest != null) infectedPlayer.setCompassTarget(nearest.getLocation());
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // updates every second

        return true;
    }

    private Player getNearestSurvivor(Player infected, List<Survivor> survivors) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Survivor s : survivors) {
            Player survivorPlayer = s.getPlayer();
            if (survivorPlayer.getWorld() != infected.getWorld()) continue;

            double distance = infected.getLocation().distance(survivorPlayer.getLocation());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = survivorPlayer;
            }
        }
        return nearest;
    }
}
