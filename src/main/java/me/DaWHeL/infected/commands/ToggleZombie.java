package me.DaWHeL.infected.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Function;

import me.DaWHeL.infected.GameManager;

public class ToggleZombie implements CommandExecutor {
    private final GameManager gameManager;
    private final Function<String, Player> playerResolver;

    public ToggleZombie(GameManager gameManager) {
        this(gameManager, Bukkit::getPlayer);
    }

    ToggleZombie(GameManager gameManager, Function<String, Player> playerResolver) {
        this.gameManager = gameManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole must specify a player: /togglezombie <player>");
                return true;
            }
            sendResult(sender, gameManager.toggleZombieSafely(player));
            return true;
        }

        Player target = playerResolver.apply(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or not online!");
            return true;
        }

        sendResult(sender, gameManager.toggleZombieSafely(target));
        return true;
    }

    private static void sendResult(CommandSender sender, me.DaWHeL.infected.RoundActionResult result) {
        sender.sendMessage((result.success() ? "§a" : "§c") + result.message());
    }
}
