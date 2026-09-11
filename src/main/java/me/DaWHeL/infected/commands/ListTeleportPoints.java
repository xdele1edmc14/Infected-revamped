package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.Arrays;
import java.util.List;

public class ListTeleportPoints implements CommandExecutor {

    private final SpawnRepository repository;

    public ListTeleportPoints(InfectedPlugin plugin) {
        this(new SpawnRepository(plugin));
    }

    ListTeleportPoints(SpawnRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<SpawnRole> roles;
        if (args.length == 0) {
            roles = Arrays.asList(SpawnRole.values());
        } else if (args.length == 1) {
            SpawnRole role = SpawnRole.fromCommandKey(args[0]).orElse(null);
            if (role == null) {
                sender.sendMessage(ChatColor.RED + "Unknown spawn role. Use survivor, release, or respawn.");
                return true;
            }
            roles = List.of(role);
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /listteleportpoints [survivor|release|respawn]");
            return true;
        }

        boolean found = roles.stream().anyMatch(role -> !repository.points(role).isEmpty());
        if (!found) {
            sender.sendMessage(ChatColor.RED + "No teleport points found!");
            return true;
        }

        for (SpawnRole role : roles) {
            for (SpawnRepository.NamedSpawn point : repository.points(role)) {
                SpawnRepository.StoredSpawn location = point.location();
                sender.sendMessage(ChatColor.AQUA + role.displayName() + " / " + point.name() + ": "
                        + ChatColor.GREEN + location.world() + " X:" + location.x()
                        + " Y:" + location.y() + " Z:" + location.z());
            }
        }

        return true;
    }
}
