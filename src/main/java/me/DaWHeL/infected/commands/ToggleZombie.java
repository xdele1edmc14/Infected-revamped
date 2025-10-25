package me.DaWHeL.infected.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.DaWHeL.infected.GameManager;

public class ToggleZombie implements CommandExecutor {
    private final GameManager gameManager;

    public ToggleZombie(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            // No arguments — toggle sender’s own status
            gameManager.toggleZombie(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer not found or not online!");
            return true;
        }

        gameManager.toggleZombie(target);
        player.sendMessage("§aToggled infected status for §e" + target.getName() + "§a!");
        return true;
    }
}
