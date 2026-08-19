package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.StartResult;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class AdminEventActions {
    private final GameManager gameManager;

    public AdminEventActions(InfectedPlugin plugin, GameManager gameManager, AdminSetupService setupService) {
        Objects.requireNonNull(plugin, "plugin");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        Objects.requireNonNull(setupService, "setupService");
    }

    public ActionResult start(Player administrator) {
        Objects.requireNonNull(administrator, "administrator");
        StartResult result = gameManager.startGame();
        return result.success()
                ? new ActionResult(true, "Starting the Infected event.")
                : new ActionResult(false, result.message());
    }

    public ActionResult stop() {
        return gameManager.stopGame()
                ? new ActionResult(true, "The Infected event is stopping.")
                : new ActionResult(false, "No Infected event is currently running or cleanup is already underway.");
    }

    public record ActionResult(boolean success, String message) {
    }
}
