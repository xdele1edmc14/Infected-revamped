package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundActionResult;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuffInfectedCommand implements CommandExecutor {

    private final GameManager gameManager;

    public BuffInfectedCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command!");
            return true;
        }

        RoundActionResult result = gameManager.toggleInfectedBuff();
        sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());

        return true;
    }
}
