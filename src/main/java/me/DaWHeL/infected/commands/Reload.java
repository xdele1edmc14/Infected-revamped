package me.DaWHeL.infected.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.DaWHeL.infected.InfectedPlugin;

public class Reload implements CommandExecutor {

    private final InfectedPlugin plugin;

    public Reload(InfectedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Reload the config
        plugin.reloadConfig();

        // Send feedback to the sender
        String msg = plugin.getConfig().getString("messages.config-reloaded",
                "&aInfected plugin configuration reloaded!");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

        return true;
    }
}
