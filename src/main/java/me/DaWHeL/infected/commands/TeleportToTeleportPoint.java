package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Optional;

public class TeleportToTeleportPoint implements CommandExecutor {
    private final SpawnRepository spawnRepository;

    public TeleportToTeleportPoint(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    TeleportToTeleportPoint(SpawnRepository spawnRepository) {
        this.spawnRepository = java.util.Objects.requireNonNull(spawnRepository, "spawnRepository");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /tttp <name>");
            return true;
        }

        String pointName = args[0];
        Optional<Location> destination = spawnRepository.loadedLocation(SpawnRole.SURVIVOR, pointName);
        if (destination.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Teleport point '" + pointName
                    + "' does not exist or its world is not loaded!");
            return true;
        }

        if (player.teleport(destination.get())) {
            player.sendMessage(ChatColor.GREEN + "Teleported to " + pointName + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Teleport to " + pointName
                    + " was cancelled by another plugin.");
        }
        return true;
    }
}
