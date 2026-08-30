package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.List;

public class ListTeleportPoints implements CommandExecutor {

    private final SpawnRepository spawnRepository;

    public ListTeleportPoints(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    ListTeleportPoints(SpawnRepository spawnRepository) {
        this.spawnRepository = java.util.Objects.requireNonNull(spawnRepository, "spawnRepository");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<SpawnRepository.NamedSpawn> points = spawnRepository.points(SpawnRole.SURVIVOR);

        if (points.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No teleport points found!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Teleport Points:");
        for (SpawnRepository.NamedSpawn point : points) {
            SpawnRepository.StoredSpawn location = point.location();
            sender.sendMessage(ChatColor.AQUA + point.name() + ": " + ChatColor.GREEN + location.world()
                    + " X:" + location.x() + " Y:" + location.y() + " Z:" + location.z());
        }

        return true;
    }
}
