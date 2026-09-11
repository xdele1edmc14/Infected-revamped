package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.List;
import java.util.Set;

public final class WeaponLootGuiListener implements Listener {
    private static final Set<ClickType> CLICKS = Set.of(
            ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);
    private static final Set<InventoryAction> BOTTOM_ACTIONS = Set.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE, InventoryAction.PICKUP_SOME,
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR);
    private final InfectedPlugin plugin;
    private final WeaponLootGuiManager manager;

    public WeaponLootGuiListener(InfectedPlugin plugin, WeaponLootGuiManager manager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WeaponMenuHolder holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) {
            if (event.getRawSlot() >= topSize && CLICKS.contains(event.getClick())
                    && BOTTOM_ACTIONS.contains(event.getAction())) return;
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getAction() == InventoryAction.NOTHING
                || !CLICKS.contains(event.getClick())) return;
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        ItemStack cursor = event.getCursor() == null ? null : event.getCursor().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (!player.hasPermission("infected.admin")) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "You no longer have permission to use weapon chest setup.");
                return;
            }
            if (cursor != null && cursor.getType().isItem() && manager.handleDrop(player, holder, slot, cursor)) return;
            manager.handleClick(player, holder, slot, click);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WeaponMenuHolder holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        List<Integer> topSlots = event.getNewItems().keySet().stream()
                .filter(slot -> slot >= 0 && slot < topSize)
                .toList();
        if (topSlots.isEmpty()) return;
        event.setCancelled(true);
        if (topSlots.size() != 1) return;
        int slot = topSlots.getFirst();
        if (!WeaponLootGuiManager.acceptsDrop(holder.type(), slot)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack original = event.getOldCursor();
        if (original == null) return;
        ItemStack item = original.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (!player.hasPermission("infected.admin")) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "You no longer have permission to use weapon chest setup.");
                return;
            }
            manager.handleDrop(player, holder, slot, item);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.closeProgress(event.getPlayer());
    }
}
