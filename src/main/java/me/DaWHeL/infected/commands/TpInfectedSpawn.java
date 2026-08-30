package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Optional;

public class TpInfectedSpawn implements CommandExecutor {

    private final SpawnRepository spawnRepository;

    public TpInfectedSpawn(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    TpInfectedSpawn(SpawnRepository spawnRepository) {
        this.spawnRepository = java.util.Objects.requireNonNull(spawnRepository, "spawnRepository");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Optional<Location> spawn = spawnRepository.loadedHoldingSpawn();
        if (spawn.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No infected spawn has been set! Use /createinfectedspawn first.");
            return true;
        }

        if (player.teleport(spawn.get())) {
            player.sendMessage(ChatColor.GREEN + "Teleported to infected spawn!");
        } else {
            player.sendMessage(ChatColor.RED
                    + "Teleport to the infected spawn was cancelled by another plugin.");
        }
        return true;
    }
}
