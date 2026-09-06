package me.DaWHeL.infected.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeaponSelectionListenerTest {
    @Test
    void taggedWandLeftClickSetsPointOneAndCancelsBlockUse() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("Infected");
        WeaponLootRepository repository = mock(WeaponLootRepository.class);
        when(repository.snapshot()).thenReturn(new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256), List.of(), List.of(), List.of()));
        WeaponSelectionListener listener = new WeaponSelectionListener(plugin, repository);
        ItemStack wand = taggedItem();
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(4);
        when(block.getY()).thenReturn(70);
        when(block.getZ()).thenReturn(-2);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(wand);
        when(event.getPlayer()).thenReturn(player);
        when(event.getClickedBlock()).thenReturn(block);

        listener.onSelect(event);

        verify(event).setCancelled(true);
        verify(repository).setPoint(1, new BlockPoint("arena", 4, 70, -2));
    }

    private static ItemStack taggedItem() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);
        return item;
    }
}
