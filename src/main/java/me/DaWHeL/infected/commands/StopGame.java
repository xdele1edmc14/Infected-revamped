package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StopGame implements CommandExecutor {

    private final GameManager gameManager;

    public StopGame(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (gameManager.isGameRunning()) {
            gameManager.stopGame();
            sender.sendMessage("Infected game has been stopped!");
        } else {
            sender.sendMessage("No game is running!");
        }
        return true;
    }
}
