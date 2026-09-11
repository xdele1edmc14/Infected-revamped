package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class TpInfectedSpawn implements CommandExecutor {

    private final SpawnRepository repository;

    public TpInfectedSpawn(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    TpInfectedSpawn(SpawnRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Location spawn = repository.loadedHoldingSpawn().orElse(null);
        if (spawn == null) {
            player.sendMessage(ChatColor.RED
                    + "No loaded, valid infected holding spawn is available. Use /createinfectedspawn first.");
            return true;
        }

        if (player.teleport(spawn)) {
            player.sendMessage(ChatColor.GREEN + "Teleported to the infected holding spawn!");
        } else {
            player.sendMessage(ChatColor.RED + "Teleport was cancelled.");
        }

        return true;
    }
}
