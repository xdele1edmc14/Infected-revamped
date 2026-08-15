package me.DaWHeL.infected.gui;

import org.bukkit.entity.Player;

public interface AdminGuiNavigator {
    void openMain(Player player);

    void openTeleportPoints(Player player, int page);
}
