package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class AdminEventActions {
    private final InfectedPlugin plugin;
    private final GameManager gameManager;
    private final AdminSetupService setupService;

    public AdminEventActions(InfectedPlugin plugin, GameManager gameManager, AdminSetupService setupService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.setupService = Objects.requireNonNull(setupService, "setupService");
    }

    public ActionResult start(Player administrator) {
        if (gameManager.isGameRunning()) {
            return new ActionResult(false, "The Infected event is already running.");
        }

        AdminSetupService.SetupSnapshot snapshot = setupService.snapshot(
                gameManager.getSurvivors().size(), gameManager.getInfected().size());
        if (!snapshot.ready()) {
            return new ActionResult(false, "Setup is incomplete. Configure the infected spawn and a teleport point first.");
        }

        boolean dispatched = plugin.getServer().dispatchCommand(administrator, "startinfected");
        return dispatched
                ? new ActionResult(true, "Starting the Infected event.")
                : new ActionResult(false, "The existing start command could not be executed.");
    }

    public ActionResult stop() {
        if (!gameManager.isGameRunning()) {
            return new ActionResult(false, "No Infected event is currently running.");
        }
        gameManager.stopGame();
        return new ActionResult(true, "The Infected event is stopping.");
    }

    public record ActionResult(boolean success, String message) {
    }
}
