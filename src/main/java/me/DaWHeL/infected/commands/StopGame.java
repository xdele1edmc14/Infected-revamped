package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
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
        RoundPhase phase = gameManager.getPhase();
        if (phase == RoundPhase.LOBBY) {
            sender.sendMessage("No game is running!");
            return true;
        }
        if (phase == RoundPhase.ENDING) {
            sender.sendMessage("The Infected game is already cleaning up.");
            return true;
        }
        if (gameManager.stopGame()) {
            sender.sendMessage("Infected game has been stopped!");
        } else {
            sender.sendMessage("The Infected game could not be stopped because its phase changed.");
        }
        return true;
    }
}
