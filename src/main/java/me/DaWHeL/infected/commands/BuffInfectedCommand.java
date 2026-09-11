package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuffInfectedCommand implements CommandExecutor {

    private final GameManager gameManager;

    public BuffInfectedCommand(GameManager gameManager, InfectedPlugin plugin) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command!");
            return true;
        }

        if (gameManager.getPhase() != RoundPhase.ACTIVE) {
            sender.sendMessage(ChatColor.RED + "Infected buffs can only be changed during active play!");
            return true;
        }

        boolean newState = !gameManager.isBuffEnabled();
        gameManager.setBuffEnabled(newState);

        if (!newState) {
            sender.sendMessage(ChatColor.RED + "Infected buffs disabled!");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Infected buffs enabled! Buffs applied to all infected.");
        return true;
    }
}
