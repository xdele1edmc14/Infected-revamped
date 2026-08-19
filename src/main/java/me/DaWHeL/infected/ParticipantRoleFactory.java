package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.entity.Player;

public interface ParticipantRoleFactory {
    Survivor createSurvivor(Player player);

    Infected createInfected(Player player);
}
