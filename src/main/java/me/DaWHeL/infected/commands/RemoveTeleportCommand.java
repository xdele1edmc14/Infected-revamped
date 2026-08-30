package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.TeleportManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RemoveTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;
    private final GameManager gameManager;

    public RemoveTeleportCommand(InfectedPlugin plugin) {
        this(plugin.getTeleportManager(), plugin.getGameManager());
    }

    RemoveTeleportCommand(TeleportManager teleportManager, GameManager gameManager) {
        this.teleportManager = java.util.Objects.requireNonNull(teleportManager, "teleportManager");
        this.gameManager = java.util.Objects.requireNonNull(gameManager, "gameManager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /removeteleport <name>");
            return true;
        }
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            sender.sendMessage("Spawn setup can only be changed while the event is in the lobby.");
            return true;
        }

        String name = args[0];
        if (teleportManager.removeTeleportPoint(name)) {
            sender.sendMessage("Teleport point " + name + " removed!");
        } else {
            sender.sendMessage("Teleport point " + name + " was not found.");
        }
        return true;
    }
}
