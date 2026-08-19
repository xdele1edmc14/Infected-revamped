package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.StartResult;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class StartGame implements CommandExecutor {
    private final GameManager gameManager;

    public StartGame(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StartResult result = gameManager.startGame();
        if (!result.success()) {
            for (String error : result.errors()) {
                sender.sendMessage(ChatColor.RED + error);
            }
        }
        return true;
    }
}
