package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameManagerCleanupTest {

    @Test
    void restoresEveryParticipantOnceBeforeRebuildingTheLobby() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        YamlConfiguration config = new YamlConfiguration();
        SpawnRepository repository = mock(SpawnRepository.class);
        TeleportManager teleports = mock(TeleportManager.class);
        PluginTaskScheduler scheduler = mock(PluginTaskScheduler.class);
        ParticipantRoleFactory roleFactory = mock(ParticipantRoleFactory.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        config.set("settings.starting-zombies", 1);
        config.set("settings.teleport-batch-size", 2);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 0);
        Location location = new Location(mock(World.class), 0, 64, 0);
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(location));
        for (SpawnRole role : SpawnRole.values()) {
            when(repository.loadedLocations(role)).thenReturn(List.of(location));
        }

        Map<SpawnRole, Consumer<TeleportBatchResult>> completions = new EnumMap<>(SpawnRole.class);
        doAnswer(invocation -> {
            completions.put(invocation.getArgument(0), invocation.getArgument(5));
            return mock(BukkitTask.class);
        }).when(teleports).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any());
        doAnswer(invocation -> {
            completions.put(invocation.getArgument(0), invocation.getArgument(7));
            return mock(BukkitTask.class);
        }).when(teleports).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any(), any(), any());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return mock(BukkitTask.class);
        }).when(scheduler).runLater(any(Runnable.class), anyLong());

        GameManager manager = spy(new GameManager(
                plugin,
                repository,
                teleports,
                scheduler,
                new RoundStartValidator(),
                new Random(1),
                roleFactory
        ));
        when(roleFactory.createInfected(any(Player.class))).thenAnswer(invocation -> {
            me.DaWHeL.infected.Roles.Infected role = mock(me.DaWHeL.infected.Roles.Infected.class);
            when(role.getPlayer()).thenReturn(invocation.getArgument(0));
            return role;
        });
        when(roleFactory.createSurvivor(any(Player.class))).thenAnswer(invocation -> survivor(
                invocation.getArgument(0)));
        doNothing().when(manager).resetPlayerState(any(Player.class));
        Player first = player("first");
        AtomicBoolean secondOnline = new AtomicBoolean(true);
        Player second = player("second", secondOnline);
        Player third = player("third");
        Player queued = player("queued");
        org.mockito.Mockito.doReturn(List.of(first, second, third, queued)).when(server).getOnlinePlayers();
        manager.addSurvivor(survivor(first));
        manager.addSurvivor(survivor(second));
        manager.addSurvivor(survivor(third));
        manager.startGame();
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));
        assertTrue(manager.queueLateJoin(queued));
        manager.getSurvivors().add(survivor(first));
        manager.getSurvivors().add(survivor(second));
        int rosterSizeBeforeStop = manager.getSurvivors().size() + manager.getInfected().size();

        assertTrue(manager.stopGame());
        assertFalse(manager.stopGame());
        assertEquals(RoundPhase.ENDING, manager.getPhase());

        ArgumentCaptor<Runnable> cleanup = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runRepeating(cleanup.capture(), anyLong(), anyLong());
        secondOnline.set(false);
        cleanup.getValue().run();
        assertEquals(rosterSizeBeforeStop,
                manager.getSurvivors().size() + manager.getInfected().size());
        secondOnline.set(true);
        cleanup.getValue().run();

        assertEquals(RoundPhase.LOBBY, manager.getPhase());
        assertEquals(4, manager.getSurvivors().size());
        assertEquals(0, manager.getInfected().size());
        verify(manager, times(1)).resetPlayerState(first);
        verify(manager, times(1)).resetPlayerState(second);
        verify(manager, times(1)).resetPlayerState(third);
        verify(manager, times(1)).resetPlayerState(queued);
        verify(roleFactory, times(1)).createSurvivor(queued);
    }

    private static Player player(String name) {
        return player(name, new AtomicBoolean(true));
    }

    private static Player player(String name, AtomicBoolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenAnswer(invocation -> online.get());
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getActivePotionEffects()).thenReturn(Set.of());
        return player;
    }

    private static Survivor survivor(Player player) {
        Survivor survivor = mock(Survivor.class);
        when(survivor.getPlayer()).thenReturn(player);
        return survivor;
    }

    private static TeleportBatchResult success(int attempted) {
        return new TeleportBatchResult(attempted, attempted, List.of(), null);
    }
}
