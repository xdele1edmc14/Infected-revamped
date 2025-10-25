package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.TeleportManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RemoveTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;

    public RemoveTeleportCommand(InfectedPlugin plugin) {
        this.teleportManager = plugin.getTeleportManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /removeteleport <name>");
            return true;
        }

        String name = args[0];
        teleportManager.removeTeleportPoint(name);
        sender.sendMessage("Teleport point " + name + " removed!");
        return true;
    }
}
