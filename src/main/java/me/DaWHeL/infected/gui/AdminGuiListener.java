package me.DaWHeL.infected.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;

public final class AdminGuiListener implements Listener {
    private static final String ADMIN_PERMISSION = "infected.admin";

    private final AdminGuiClickHandler clickHandler;

    public AdminGuiListener(AdminGuiClickHandler clickHandler) {
        this.clickHandler = Objects.requireNonNull(clickHandler, "clickHandler");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        AdminMenuHolder holder = holder(event.getView());
        if (holder == null) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You no longer have permission to use the Infected admin controls.");
            return;
        }
        if (event.getAction() == InventoryAction.NOTHING) {
            return;
        }
        if (!AdminGuiPolicy.isTopInventoryClick(event.getRawSlot(), event.getView().getTopInventory().getSize())) {
            return;
        }

        clickHandler.handleClick(player, holder, event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (holder(event.getView()) != null) {
            event.setCancelled(true);
        }
    }

    private static AdminMenuHolder holder(InventoryView view) {
        if (view.getTopInventory().getHolder() instanceof AdminMenuHolder holder) {
            return holder;
        }
        return null;
    }
}
