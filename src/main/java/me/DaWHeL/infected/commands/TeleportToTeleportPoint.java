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

public class TeleportToTeleportPoint implements CommandExecutor {
    private final SpawnRepository repository;

    public TeleportToTeleportPoint(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    TeleportToTeleportPoint(SpawnRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.RED + "Usage: /tttp [survivor|release|respawn] <name>");
            return true;
        }

        SpawnRole role = args.length == 1 ? SpawnRole.SURVIVOR
                : SpawnRole.fromCommandKey(args[0]).orElse(null);
        if (role == null) {
            player.sendMessage(ChatColor.RED + "Unknown spawn role. Use survivor, release, or respawn.");
            return true;
        }
        String pointName = args[args.length - 1];

        Location destination = repository.loadedPoint(role, pointName).orElse(null);
        if (destination == null) {
            player.sendMessage(ChatColor.RED + role.displayName() + " spawn '" + pointName
                    + "' is missing, malformed, or in an unloaded world.");
            return true;
        }

        if (player.teleport(destination)) {
            player.sendMessage(ChatColor.GREEN + "Teleported to " + role.displayName() + " spawn "
                    + pointName + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Teleport was cancelled.");
        }
        return true;
    }
}
