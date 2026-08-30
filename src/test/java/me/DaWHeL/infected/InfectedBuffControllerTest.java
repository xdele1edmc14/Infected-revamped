package me.DaWHeL.infected;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedBuffControllerTest {

    @Test
    void enablingTwiceAppliesBuffEffectsAndAddsOnlyOneOwnedCompass() {
        NamespacedKey key = new NamespacedKey("infected", "tracking_compass");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack ordinaryCompass = compass(key, false);
        ItemStack createdCompass = compass(key, false);
        List<InfectedBuffController.EffectProfile> profiles = new ArrayList<>();
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(
                new ItemStack[]{ordinaryCompass},
                new ItemStack[]{ordinaryCompass, createdCompass});
        InfectedBuffController controller = new InfectedBuffController(
                key, () -> createdCompass, (target, profile) -> profiles.add(profile));

        controller.apply(player, true);
        controller.apply(player, true);

        verify(inventory, times(1)).addItem(createdCompass);
        assertEquals(List.of(
                new InfectedBuffController.EffectProfile(1, true),
                new InfectedBuffController.EffectProfile(1, true)), profiles);
        assertTrue(controller.isOwnedCompass(createdCompass));
        assertFalse(controller.isOwnedCompass(ordinaryCompass));
    }

    @Test
    void disablingRemovesOnlyOwnedCompassesAndRestoresNormalSpeed() {
        NamespacedKey key = new NamespacedKey("infected", "tracking_compass");
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack ordinaryCompass = compass(key, false);
        ItemStack ownedCompass = compass(key, true);
        List<InfectedBuffController.EffectProfile> profiles = new ArrayList<>();
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(
                new ItemStack[]{ordinaryCompass, ownedCompass});
        InfectedBuffController controller = new InfectedBuffController(
                key, () -> ownedCompass, (target, profile) -> profiles.add(profile));

        controller.apply(player, false);

        verify(inventory, never()).setItem(0, null);
        verify(inventory).setItem(1, null);
        assertEquals(List.of(new InfectedBuffController.EffectProfile(0, false)), profiles);
    }

    private static ItemStack compass(NamespacedKey key, boolean owned) {
        ItemStack compass = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        AtomicBoolean tagged = new AtomicBoolean(owned);
        when(compass.getType()).thenReturn(Material.COMPASS);
        when(compass.hasItemMeta()).thenReturn(true);
        when(compass.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(key, PersistentDataType.BYTE)).thenAnswer(invocation -> tagged.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            tagged.set(true);
            return null;
        }).when(data).set(key, PersistentDataType.BYTE, (byte) 1);
        return compass;
    }
}
