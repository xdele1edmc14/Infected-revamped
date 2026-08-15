package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;
import java.util.Set;

public final class AdminGuiListener implements Listener {
    private static final String ADMIN_PERMISSION = "infected.admin";
    private static final Set<org.bukkit.event.inventory.ClickType> ACTION_CLICKS = Set.of(
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.ClickType.RIGHT,
            org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
    );

    private final InfectedPlugin plugin;
    private final AdminGuiClickHandler clickHandler;

    public AdminGuiListener(InfectedPlugin plugin, AdminGuiClickHandler clickHandler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        if (event.getAction() == InventoryAction.NOTHING) {
            return;
        }
        if (!ACTION_CLICKS.contains(event.getClick())) {
            return;
        }
        if (!AdminGuiPolicy.isTopInventoryClick(event.getRawSlot(), event.getView().getTopInventory().getSize())) {
            return;
        }

        int rawSlot = event.getRawSlot();
        org.bukkit.event.inventory.ClickType clickType = event.getClick();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isSameMenuOpen(player, holder)) {
                return;
            }
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED
                        + "You no longer have permission to use the Infected admin controls.");
                return;
            }
            clickHandler.handleClick(player, holder, rawSlot, clickType);
        });
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

    private static boolean isSameMenuOpen(Player player, AdminMenuHolder expected) {
        return player.getOpenInventory().getTopInventory().getHolder() == expected;
    }
}
