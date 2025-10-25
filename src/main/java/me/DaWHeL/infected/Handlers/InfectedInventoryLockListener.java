package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryHolder;

public class InfectedInventoryLockListener implements Listener {

    private final GameManager gameManager;

    public InfectedInventoryLockListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    private boolean isInfected(Player player) {
        return gameManager.getInfected().stream()
                .anyMatch(inf -> inf.getPlayer().equals(player));
    }

    // Prevent any inventory modification
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isInfected(player)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isInfected(player)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    // Prevent dropping items
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isInfected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // Prevent picking up items
    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (isInfected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // Prevent swapping items with hotbar (e.g. pressing numbers)
    @EventHandler
    public void onHotbarSwap(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Player player && isInfected(player)) {
            event.setCancelled(true);
        }
    }

    // Prevent offhand swapping
    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        if (isInfected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!isInfected(player)) return;

        Block block = event.getClickedBlock();
        Material type = block.getType();

        // If the block has an inventory (container), cancel it
        if (block.getState() instanceof InventoryHolder) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Zombies cannot open containers!");
        }
    }
}
