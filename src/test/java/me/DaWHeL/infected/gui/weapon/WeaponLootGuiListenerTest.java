package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class WeaponLootGuiListenerTest {
    @Test
    void quittingRemovesTheAdminsOperationBossBar() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        WeaponLootGuiManager manager = mock(WeaponLootGuiManager.class);
        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new WeaponLootGuiListener(plugin, manager).onQuit(event);

        verify(manager).closeProgress(player);
    }

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
    void dragIntoDesignatedInputUsesTheWholeOriginalCursorStack() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        WeaponLootGuiManager manager = mock(WeaponLootGuiManager.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        ItemStack original = mock(ItemStack.class);
        ItemStack exactCopy = mock(ItemStack.class);
        ItemStack divided = mock(ItemStack.class);
        WeaponMenuHolder holder = WeaponMenuHolder.page(WeaponMenuHolder.MenuType.GUNS, 0, Map.of());
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(54);
        when(event.getNewItems()).thenReturn(Map.of(49, divided));
        when(event.getOldCursor()).thenReturn(original);
        when(original.clone()).thenReturn(exactCopy);
        when(event.getWhoClicked()).thenReturn(player);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        new WeaponLootGuiListener(plugin, manager).onDrag(event);

        verify(manager).handleDrop(player, holder, 49, exactCopy);
        verify(divided, never()).clone();
    }

    @Test
    void dragAcrossMultipleTopSlotsDoesNotGuessWhichInputWasIntended() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        WeaponLootGuiManager manager = mock(WeaponLootGuiManager.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        WeaponMenuHolder holder = WeaponMenuHolder.page(WeaponMenuHolder.MenuType.GUNS, 0, Map.of());
        Map<Integer, ItemStack> divided = new LinkedHashMap<>();
        divided.put(10, mock(ItemStack.class));
        divided.put(49, mock(ItemStack.class));
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(54);
        when(event.getNewItems()).thenReturn(divided);
        when(event.getWhoClicked()).thenReturn(player);

        new WeaponLootGuiListener(plugin, manager).onDrag(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(manager);
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
