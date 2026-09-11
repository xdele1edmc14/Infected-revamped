package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.TeleportManager;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RemoveTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;

    public RemoveTeleportCommand(InfectedPlugin plugin) {
        this.teleportManager = plugin.getTeleportManager();
    }

    RemoveTeleportCommand(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("Usage: /removeteleport [survivor|release|respawn] <name>");
            return true;
        }

        SpawnRole role = args.length == 1 ? SpawnRole.SURVIVOR
                : SpawnRole.fromCommandKey(args[0]).orElse(null);
        if (role == null) {
            sender.sendMessage("Unknown spawn role. Use survivor, release, or respawn.");
            return true;
        }
        String name = args[args.length - 1];
        if (teleportManager.removeTeleportPoint(role, name)) {
            sender.sendMessage(role.displayName() + " spawn " + name + " removed!");
        } else {
            sender.sendMessage(role.displayName() + " spawn " + name + " was not found.");
        }
        return true;
    }
}
