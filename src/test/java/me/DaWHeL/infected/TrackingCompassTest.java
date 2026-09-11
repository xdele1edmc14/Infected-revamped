package me.DaWHeL.infected;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingCompassTest {

    @Test
    void addsTheTrackingCompassWhenTheZombieDoesNotHaveOne() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack compass = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.contains(Material.COMPASS)).thenReturn(false);

        new TrackingCompass(() -> compass).ensurePresent(player);

        verify(inventory).addItem(compass);
    }

    @Test
    void doesNotDuplicateAnExistingTrackingCompass() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack compass = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.contains(Material.COMPASS)).thenReturn(true);

        new TrackingCompass(() -> compass).ensurePresent(player);

        verify(inventory, never()).addItem(compass);
    }

    @Test
    void removesTheTrackingCompassWhenTrackingIsDisabled() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        new TrackingCompass(() -> mock(ItemStack.class)).remove(player);

        verify(inventory).remove(Material.COMPASS);
    }
}
