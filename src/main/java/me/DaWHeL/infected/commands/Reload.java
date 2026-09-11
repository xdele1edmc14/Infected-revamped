package me.DaWHeL.infected.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;

public class Reload implements CommandExecutor {

    private final InfectedPlugin plugin;
    private final GameManager gameManager;
    private final Runnable runtimeRefresh;

    public Reload(InfectedPlugin plugin, GameManager gameManager) {
        this(plugin, gameManager, () -> { });
    }

    public Reload(InfectedPlugin plugin, GameManager gameManager, Runnable runtimeRefresh) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.runtimeRefresh = runtimeRefresh;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            sender.sendMessage(ChatColor.RED
                    + "Infected configuration can only be reloaded in the lobby.");
            return true;
        }

        plugin.reloadConfig();
        runtimeRefresh.run();

        // Send feedback to the sender
        String msg = plugin.getConfig().getString("messages.config-reloaded",
                "&aInfected plugin configuration reloaded!");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        return true;
    }
}
