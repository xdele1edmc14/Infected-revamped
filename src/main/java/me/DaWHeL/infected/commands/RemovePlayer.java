package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Function;

public class RemovePlayer implements CommandExecutor {

    private final GameManager gameManager;
    private final Function<String, Player> playerResolver;

    public RemovePlayer(GameManager gameManager) {
        this(gameManager, Bukkit::getPlayer);
    }

    RemovePlayer(GameManager gameManager, Function<String, Player> playerResolver) {
        this.gameManager = gameManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /removeplayer <player>");
            return true;
        }

        Player target = playerResolver.apply(args[0]);

        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline!");
            return true;
        }

        me.DaWHeL.infected.RoundActionResult result = gameManager.removePlayer(target);
        sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());

        return true;
    }
}
