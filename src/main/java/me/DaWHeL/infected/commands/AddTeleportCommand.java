package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.TeleportManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddTeleportCommand implements CommandExecutor {

    private final TeleportManager teleportManager;

    public AddTeleportCommand(InfectedPlugin plugin) {
        this.teleportManager = plugin.getTeleportManager();
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

        String name = args[0];
        teleportManager.addTeleportPoint(player, name);
        return true;
    }
}
