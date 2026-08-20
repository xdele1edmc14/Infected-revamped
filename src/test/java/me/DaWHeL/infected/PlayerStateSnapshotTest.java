package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStateSnapshotTest {

    @Test
    void restoresDefensiveCopiesOfEveryPluginOwnedPlayerValue() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Server server = mock(Server.class);
        World capturedWorld = mock(World.class);
        World currentWorld = mock(World.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        UUID worldId = UUID.randomUUID();
        Location capturedLocation = new Location(capturedWorld, 2.5, 70, -4.5, 90, 10);
        Location compassTarget = new Location(capturedWorld, 30, 72, 15);
        ItemStack storageOriginal = mock(ItemStack.class);
        ItemStack storageCaptured = mock(ItemStack.class);
        ItemStack storageRestored = mock(ItemStack.class);
        ItemStack armorOriginal = mock(ItemStack.class);
        ItemStack armorCaptured = mock(ItemStack.class);
        ItemStack armorRestored = mock(ItemStack.class);
        ItemStack offhandOriginal = mock(ItemStack.class);
        ItemStack offhandCaptured = mock(ItemStack.class);
        ItemStack offhandRestored = mock(ItemStack.class);
        when(storageOriginal.clone()).thenReturn(storageCaptured);
        when(storageCaptured.clone()).thenReturn(storageRestored);
        when(storageRestored.getAmount()).thenReturn(32);
        when(armorOriginal.clone()).thenReturn(armorCaptured);
        when(armorCaptured.clone()).thenReturn(armorRestored);
        when(armorRestored.getAmount()).thenReturn(1);
        when(offhandOriginal.clone()).thenReturn(offhandCaptured);
        when(offhandCaptured.clone()).thenReturn(offhandRestored);
        when(offhandRestored.getAmount()).thenReturn(1);
        ItemStack[] storage = {storageOriginal};
        ItemStack[] armor = {armorOriginal};
        PotionEffect originalEffect = mock(PotionEffect.class);
        PotionEffect pluginEffect = mock(PotionEffect.class);
        Component listName = Component.text("Original");
        Component header = Component.text("Header");
        Component footer = Component.text("Footer");

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.getArmorContents()).thenReturn(armor);
        when(inventory.getItemInOffHand()).thenReturn(offhandOriginal);
        when(inventory.getHeldItemSlot()).thenReturn(4);
        when(player.getLocation()).thenReturn(capturedLocation);
        when(capturedWorld.getUID()).thenReturn(worldId);
        when(player.getServer()).thenReturn(server);
        when(server.getWorld(worldId)).thenReturn(capturedWorld);
        when(player.getWorld()).thenReturn(currentWorld);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(player.isGlowing()).thenReturn(true);
        doReturn(List.of(originalEffect), List.of(pluginEffect))
                .when(player).getActivePotionEffects();
        when(player.playerListName()).thenReturn(listName);
        when(player.playerListHeader()).thenReturn(header);
        when(player.playerListFooter()).thenReturn(footer);
        when(player.getScoreboard()).thenReturn(scoreboard);
        when(player.getCompassTarget()).thenReturn(compassTarget);
        when(player.getFoodLevel()).thenReturn(17);
        when(player.getSaturation()).thenReturn(3.5f);
        when(player.getExhaustion()).thenReturn(1.25f);

        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(player);

        storage[0] = mock(ItemStack.class);
        armor[0] = mock(ItemStack.class);
        capturedLocation.setX(999);
        compassTarget.setZ(999);

        snapshot.restore(player);

        ArgumentCaptor<ItemStack[]> restoredStorage = ArgumentCaptor.forClass(ItemStack[].class);
        ArgumentCaptor<ItemStack[]> restoredArmor = ArgumentCaptor.forClass(ItemStack[].class);
        ArgumentCaptor<ItemStack> restoredOffhand = ArgumentCaptor.forClass(ItemStack.class);
        ArgumentCaptor<Location> restoredLocation = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<Location> restoredCompass = ArgumentCaptor.forClass(Location.class);
        verify(inventory).setStorageContents(restoredStorage.capture());
        verify(inventory).setArmorContents(restoredArmor.capture());
        verify(inventory).setItemInOffHand(restoredOffhand.capture());
        verify(player).teleport(restoredLocation.capture());
        verify(player).setCompassTarget(restoredCompass.capture());

        assertEquals(32, restoredStorage.getValue()[0].getAmount());
        assertEquals(1, restoredArmor.getValue()[0].getAmount());
        assertEquals(1, restoredOffhand.getValue().getAmount());
        assertNotSame(storage, restoredStorage.getValue());
        assertEquals(2.5, restoredLocation.getValue().getX());
        assertEquals(15, restoredCompass.getValue().getZ());
        verify(inventory).setHeldItemSlot(4);
        verify(player).setGameMode(GameMode.ADVENTURE);
        verify(player).setGlowing(true);
        verify(player).playerListName(listName);
        verify(player).sendPlayerListHeaderAndFooter(header, footer);
        verify(player).setScoreboard(scoreboard);
        verify(player).setFoodLevel(17);
        verify(player).setSaturation(3.5f);
        verify(player).setExhaustion(1.25f);
        verify(player).addPotionEffect(originalEffect);

        InOrder effectOrder = inOrder(player);
        effectOrder.verify(player).removePotionEffect((PotionEffectType) null);
        effectOrder.verify(player).addPotionEffect(originalEffect);
    }

    @Test
    void fallsBackToCurrentWorldSpawnWhenCapturedWorldIsUnavailable() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Server server = mock(Server.class);
        World capturedWorld = mock(World.class);
        World currentWorld = mock(World.class);
        UUID worldId = UUID.randomUUID();
        Location capturedLocation = new Location(capturedWorld, 2, 70, 3);
        Location fallback = new Location(currentWorld, 0, 64, 0);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(inventory.getItemInOffHand()).thenReturn(null);
        when(player.getLocation()).thenReturn(capturedLocation);
        when(capturedWorld.getUID()).thenReturn(worldId);
        when(player.getServer()).thenReturn(server);
        when(server.getWorld(worldId)).thenReturn(null);
        when(player.getWorld()).thenReturn(currentWorld);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(currentWorld.getSpawnLocation()).thenReturn(fallback);
        doReturn(List.of()).when(player).getActivePotionEffects();
        when(player.getCompassTarget()).thenReturn(new Location(capturedWorld, 1, 2, 3));

        PlayerStateSnapshot.capture(player).restore(player);

        verify(player).teleport(fallback);
        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void continuesRestoringAfterOnePropertyFails() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        Location location = new Location(world, 2, 70, 3);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(player.getLocation()).thenReturn(location);
        when(world.getUID()).thenReturn(worldId);
        when(player.getServer()).thenReturn(server);
        when(server.getWorld(worldId)).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        doReturn(List.of()).when(player).getActivePotionEffects();
        when(player.getCompassTarget()).thenReturn(location);
        doThrow(new IllegalStateException("inventory unavailable"))
                .when(inventory).setStorageContents(any(ItemStack[].class));
        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(player);

        Logger logger = Logger.getLogger(PlayerStateSnapshot.class.getName());
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        try {
            assertDoesNotThrow(() -> snapshot.restore(player));
        } finally {
            logger.setLevel(previousLevel);
        }

        verify(player).setGameMode(GameMode.ADVENTURE);
        verify(player).teleport(any(Location.class));
    }
}
