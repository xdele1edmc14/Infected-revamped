package me.DaWHeL.infected.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.TeleportManager;
import me.DaWHeL.infected.Roles.Survivor;
import me.DaWHeL.infected.Roles.Infected;

import java.util.ArrayList;
import java.util.List;

public class StartGame implements CommandExecutor {

    private final GameManager gameManager;
    private final InfectedPlugin plugin;
    private final TeleportManager teleportManager;

    public StartGame(GameManager gameManager, InfectedPlugin plugin) {
        this.gameManager = gameManager;
        this.plugin = plugin;
        this.teleportManager = plugin.getTeleportManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (gameManager.isGameRunning()) {
            String msg = plugin.getConfig().getString("messages.already-running", "&cGame already running!");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }

        // Start the game
        gameManager.startGame();
        String startMessage = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.game-start", "&aThe Infected game has started!"));
        Bukkit.broadcastMessage(startMessage.replace("{zombies}", String.valueOf(gameManager.getInfected().size())));


        // Convert Survivors and Infected role objects to Player lists
        List<Player> survivorPlayers = new ArrayList<>();
        for (Survivor s : gameManager.getSurvivors()) {
            survivorPlayers.add(s.getPlayer());
        }

        List<Player> infectedPlayers = new ArrayList<>();
        for (Infected i : gameManager.getInfected()) {
            infectedPlayers.add(i.getPlayer());
        }

        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 5);
        int delayTicks = plugin.getConfig().getInt("settings.teleport-delay", 40); // 1 second = 20 ticks
        int infectedDelaySeconds = plugin.getConfig().getInt("settings.infected-teleport-delay", 10);

        // Teleport survivors in batches
        teleportManager.teleportPlayersBatch(survivorPlayers, batchSize, delayTicks, () -> {

            // Broadcast survivors teleporting message
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.survivors-teleporting",
                            "&bSurvivors are being teleported...")));

            // After all survivors teleported, teleport infected after delay

            // Broadcast infected teleporting message
            String broadcastMSG = plugin.getConfig().getString("messages.zombies-teleporting",
                    "&cInfected zombies will be teleported in {time} seconds...");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMSG.replace("{time}", String.valueOf(infectedDelaySeconds))));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {

                // Teleport infected in batches
                teleportManager.teleportPlayersBatch(infectedPlayers, batchSize, delayTicks, null);

            }, infectedDelaySeconds * 20L); // delay in ticks
        });

        return true;
    }
}

