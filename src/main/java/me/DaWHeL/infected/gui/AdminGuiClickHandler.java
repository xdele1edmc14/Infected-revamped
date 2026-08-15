package me.DaWHeL.infected.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public interface AdminGuiClickHandler {
    void handleClick(Player player, AdminMenuHolder holder, int rawSlot, ClickType clickType);
}
