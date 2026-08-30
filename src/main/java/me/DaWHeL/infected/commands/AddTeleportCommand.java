package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.TeleportManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;
    private final GameManager gameManager;

    public AddTeleportCommand(InfectedPlugin plugin) {
        this(plugin.getTeleportManager(), plugin.getGameManager());
    }

    AddTeleportCommand(TeleportManager teleportManager, GameManager gameManager) {
        this.teleportManager = java.util.Objects.requireNonNull(teleportManager, "teleportManager");
        this.gameManager = java.util.Objects.requireNonNull(gameManager, "gameManager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("Usage: /addteleport <name>");
            return true;
        }
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            player.sendMessage("Spawn setup can only be changed while the event is in the lobby.");
            return true;
        }

        String name = args[0];
        teleportManager.addTeleportPoint(player, name);
        return true;
    }
}
