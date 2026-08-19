package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class BukkitParticipantRoleFactory implements ParticipantRoleFactory {
    private final InfectedPlugin plugin;

    public BukkitParticipantRoleFactory(InfectedPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Survivor createSurvivor(Player player) {
        return new Survivor(player);
    }

    @Override
    public Infected createInfected(Player player) {
        return new Infected(plugin, player, false);
    }
}
