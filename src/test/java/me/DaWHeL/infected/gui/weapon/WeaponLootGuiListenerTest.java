package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

class WeaponLootGuiListenerTest {
    @Test
    void cancelsDraggingIntoAWeaponMenu() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        ItemStack dropped = mock(ItemStack.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD));
        when(inventory.getSize()).thenReturn(54);
        when(event.getNewItems()).thenReturn(Map.of(10, dropped));

        new WeaponLootGuiListener(plugin, mock(WeaponLootGuiManager.class)).onDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void bottomInventoryShiftClickIsCancelledWithoutDispatch() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        WeaponLootGuiManager manager = mock(WeaponLootGuiManager.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        WeaponMenuHolder holder = WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(54);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        when(event.getRawSlot()).thenReturn(60);

        new WeaponLootGuiListener(plugin, manager).onClick(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(manager);
    }

    @Test
    void ordinaryBottomInventoryPickupRemainsAvailableForCursorDropConfiguration() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        WeaponLootGuiManager manager = mock(WeaponLootGuiManager.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(WeaponMenuHolder.root(WeaponMenuHolder.MenuType.GUNS));
        when(inventory.getSize()).thenReturn(54);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getRawSlot()).thenReturn(60);

        new WeaponLootGuiListener(plugin, manager).onClick(event);

        verify(event, never()).setCancelled(true);
        verifyNoInteractions(manager);
    }
}
