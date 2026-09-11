package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.TeleportManager;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;

    public AddTeleportCommand(InfectedPlugin plugin) {
        this.teleportManager = plugin.getTeleportManager();
    }

    AddTeleportCommand(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage("Usage: /addteleport [survivor|release|respawn] <name>");
            return true;
        }

        SpawnRole role = args.length == 1 ? SpawnRole.SURVIVOR
                : SpawnRole.fromCommandKey(args[0]).orElse(null);
        if (role == null) {
            player.sendMessage("Unknown spawn role. Use survivor, release, or respawn.");
            return true;
        }
        String name = args[args.length - 1];
        teleportManager.addTeleportPoint(player, role, name);
        return true;
    }
}
