package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RemovePlayer implements CommandExecutor {

    private final GameManager gameManager;

    public RemovePlayer(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /removesurvivor <player>");
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline!");
            return false;
        }

        // Find and remove the Survivor object
        Survivor toRemove = null;
        for (Survivor s : gameManager.getSurvivors()) {
            if (s.getPlayer().equals(target)) {
                toRemove = s;
                break;
            }
        }

        if (toRemove != null) {
            gameManager.getSurvivors().remove(toRemove);

            // Use GameManager's resetPlayer method
            gameManager.resetPlayer(target);

            sender.sendMessage(ChatColor.GREEN + target.getName() + " has been removed from survivors! To add him back, he needs to rejoin.");
        } else {
            sender.sendMessage(ChatColor.RED + "That player is not currently a survivor! Make sure they are not infected.");
            sender.sendMessage(ChatColor.RED + "If they are infected, use /togglezombie to change their status.");
        }

        return true;
    }
}
