package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.SpawnRole;
import org.bukkit.entity.Player;

public interface AdminGuiNavigator {
    void openMain(Player player);

    void openTeleportPoints(Player player, SpawnRole role, int page);

    default void openTeleportPoints(Player player, int page) {
        openTeleportPoints(player, SpawnRole.SURVIVOR, page);
    }
}
