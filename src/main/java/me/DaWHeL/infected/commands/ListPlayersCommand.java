package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ListPlayersCommand implements CommandExecutor {
    private final GameManager gameManager;

    public ListPlayersCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        // List Survivors
        StringBuilder survivorsList = new StringBuilder();
        for (Survivor s : gameManager.getSurvivors()) {
            if (survivorsList.length() > 0) survivorsList.append(ChatColor.GRAY + ", ");
            survivorsList.append(ChatColor.GREEN).append(s.getPlayer().getName());
        }

        // List Infected
        StringBuilder infectedList = new StringBuilder();
        for (Infected i : gameManager.getInfected()) {
            if (infectedList.length() > 0) infectedList.append(ChatColor.GRAY + ", ");
            infectedList.append(ChatColor.RED).append(i.getPlayer().getName());
        }

        player.sendMessage(ChatColor.GOLD + "------ Infected Game Players ------");
        player.sendMessage(ChatColor.GREEN + "Survivors (" + gameManager.getSurvivors().size() + "): " +
                (survivorsList.length() > 0 ? survivorsList.toString() : ChatColor.GRAY + "None"));
        player.sendMessage(ChatColor.RED + "Infected (" + gameManager.getInfected().size() + "): " +
                (infectedList.length() > 0 ? infectedList.toString() : ChatColor.GRAY + "None"));
        player.sendMessage(ChatColor.GOLD + "----------------------------------");

        return true;
    }
}
