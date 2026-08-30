package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class BukkitParticipantRoleFactory implements ParticipantRoleFactory {
    private final InfectedPlugin plugin;
    private final InfectedRoleEquipment roleEquipment;

    public BukkitParticipantRoleFactory(InfectedPlugin plugin) {
        this(plugin, new InfectedRoleEquipment(plugin));
    }

    BukkitParticipantRoleFactory(InfectedPlugin plugin, InfectedRoleEquipment roleEquipment) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.roleEquipment = Objects.requireNonNull(roleEquipment, "roleEquipment");
    }

    @Override
    public Survivor createSurvivor(Player player) {
        return new Survivor(player);
    }

    @Override
    public Infected createInfected(Player player) {
        return new Infected(plugin, player, false, roleEquipment);
    }
}
