package me.DaWHeL.infected.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpInfectedCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "---------- " + ChatColor.RED + "Infected Plugin Help" + ChatColor.GOLD + " ----------");
        sender.sendMessage(ChatColor.YELLOW + "/startinfected" + ChatColor.GRAY + " - Starts the Infected game");
        sender.sendMessage(ChatColor.YELLOW + "/stopinfected" + ChatColor.GRAY + " - Stops the Infected game");
        sender.sendMessage(ChatColor.YELLOW + "/buffinfected" + ChatColor.GRAY + " - Gives all infected players a buff");
        sender.sendMessage(ChatColor.YELLOW + "/listplayers" + ChatColor.GRAY + " - Shows all survivors and infected");
        sender.sendMessage(ChatColor.YELLOW + "/reloadinfected" + ChatColor.GRAY + " - Reloads config in the lobby");
        sender.sendMessage(ChatColor.YELLOW + "/addteleport <pointName>" + ChatColor.GRAY + " - Adds a new teleport point");
        sender.sendMessage(ChatColor.YELLOW + "/removeteleport <pointName>" + ChatColor.GRAY + " - Removes a teleport point");
        sender.sendMessage(ChatColor.YELLOW + "/listteleportpoints" + ChatColor.GRAY + " - Lists all teleport points");
        sender.sendMessage(ChatColor.YELLOW + "/tttp <pointName>" + ChatColor.GRAY + " - Teleport to a teleport point");
        sender.sendMessage(ChatColor.YELLOW + "/togglezombie <name>" + ChatColor.GRAY + " - Safely toggles an active player");
        sender.sendMessage(ChatColor.YELLOW + "/removeplayer <name>" + ChatColor.GRAY + " - Safely removes a player from the round");
        sender.sendMessage(ChatColor.YELLOW + "/createinfectedspawn" + ChatColor.GRAY + " - Sets zombie spawn at the beggining of the game");
        sender.sendMessage(ChatColor.YELLOW + "/tpinfectedspawn" + ChatColor.GRAY + " - Teleports you to the infected spawn point");
        sender.sendMessage(ChatColor.YELLOW + "/givefeather" + ChatColor.GRAY + " - Gives you a test Jump Feather");
        sender.sendMessage(ChatColor.YELLOW + "/helpinfected" + ChatColor.GRAY + " - Shows this help menu");
        sender.sendMessage(ChatColor.GOLD + "------------------------------------------");
        return true;
    }
}
