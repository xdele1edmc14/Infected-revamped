package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.gui.AdminSetupService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateInfectedSpawn implements CommandExecutor {

    private final GameManager gameManager;
    private final AdminSetupService setupService;

    public CreateInfectedSpawn(InfectedPlugin plugin) {
        this(plugin.getGameManager(), new AdminSetupService(plugin));
    }

    CreateInfectedSpawn(GameManager gameManager, AdminSetupService setupService) {
        this.gameManager = java.util.Objects.requireNonNull(gameManager, "gameManager");
        this.setupService = java.util.Objects.requireNonNull(setupService, "setupService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            player.sendMessage(ChatColor.RED
                    + "Spawn setup can only be changed while the event is in the lobby.");
            return true;
        }

        Location loc = player.getLocation();
        try {
            setupService.setInfectedSpawn(loc);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Infected spawn set at your current location!");
        return true;
    }
}
