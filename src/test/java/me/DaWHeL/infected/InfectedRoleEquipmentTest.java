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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedRoleEquipmentTest {
    @Test
    void createdHeadIsTaggedAsPluginOwned() {
        NamespacedKey key = new NamespacedKey("infected", "infected_role_head");
        ItemStack head = head(key, false);
        InfectedRoleEquipment equipment = new InfectedRoleEquipment(key, () -> head);

        ItemStack created = equipment.createHead();

        assertTrue(equipment.isOwnedHead(created));
    }

    @Test
    void cleanupRemovesOnlyOwnedRoleHeads() {
        NamespacedKey key = new NamespacedKey("infected", "infected_role_head");
        InfectedRoleEquipment equipment = new InfectedRoleEquipment(key, () -> head(key, false));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack legitimateHead = head(key, false);
        ItemStack ownedHead = head(key, true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(legitimateHead, ownedHead);

        assertFalse(equipment.removeOwnedHead(player));
        verify(inventory, never()).setHelmet(null);

        assertTrue(equipment.removeOwnedHead(player));
        verify(inventory).setHelmet(null);
    }

    private static ItemStack head(NamespacedKey key, boolean owned) {
        ItemStack head = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        AtomicBoolean tagged = new AtomicBoolean(owned);
        AtomicBoolean applied = new AtomicBoolean(owned);
        when(head.getType()).thenReturn(Material.ZOMBIE_HEAD);
        when(head.hasItemMeta()).thenReturn(true);
        when(head.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(key, PersistentDataType.BYTE)).thenAnswer(
                invocation -> tagged.get() && applied.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            tagged.set(true);
            return null;
        }).when(data).set(key, PersistentDataType.BYTE, (byte) 1);
        org.mockito.Mockito.doAnswer(invocation -> {
            applied.set(true);
            return true;
        }).when(head).setItemMeta(meta);
        return head;
    }
}
