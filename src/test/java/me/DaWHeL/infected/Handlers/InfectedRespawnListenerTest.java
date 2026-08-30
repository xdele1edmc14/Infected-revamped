package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedRoleEquipment;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class InfectedRespawnListenerTest {
    private GameManager gameManager;
    private SpawnRepository spawnRepository;
    private InfectedRespawnListener listener;
    private Player player;
    private PlayerRespawnEvent event;
    private BukkitScheduler scheduler;
    private me.DaWHeL.infected.InfectedPlugin plugin;
    private InfectedRoleEquipment roleEquipment;
    private ItemStack infectedHead;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        spawnRepository = mock(SpawnRepository.class);
        roleEquipment = mock(InfectedRoleEquipment.class);
        infectedHead = mock(ItemStack.class);
        when(roleEquipment.createHead()).thenReturn(infectedHead);
        listener = new InfectedRespawnListener(
                gameManager, spawnRepository, new Random(1), roleEquipment);
        player = mock(Player.class);
        event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.claimInfectedRespawn(player)).thenReturn(true);
        Server server = mock(Server.class);
        plugin = mock(me.DaWHeL.infected.InfectedPlugin.class);
        scheduler = mock(BukkitScheduler.class);
        when(gameManager.getPlugin()).thenReturn(plugin);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
    }

    @Test
    void activeInfectedRespawnsUseOnlyTheRespawnRole() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Location respawn = new Location(world, 20.5, 70, 30.5);
        Block ground = block(Material.STONE, false);
        Block feet = block(Material.AIR, true);
        Block head = block(Material.AIR, true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(20, 69, 30)).thenReturn(ground);
        when(world.getBlockAt(20, 70, 30)).thenReturn(feet);
        when(world.getBlockAt(20, 71, 30)).thenReturn(head);
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN)).thenReturn(java.util.List.of(respawn));
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(20, 69, 30, 21, 70, 31));

        listener.onPlayerRespawn(event);

        verify(event).setRespawnLocation(respawn);
        verify(gameManager).scheduleRoundLater(
                org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(RoundPhase.ACTIVE),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(Runnable.class));
        verify(spawnRepository, never()).loadedLocations(SpawnRole.SURVIVOR);
        verify(spawnRepository, never()).loadedLocations(SpawnRole.INFECTED_RELEASE);
    }

    @Test
    void delayedRespawnLoadoutDelegatesTheCurrentBuffStateToTheRoundManager() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Location respawn = new Location(world, 20.5, 70, 30.5);
        Block ground = block(Material.STONE, false);
        Block feet = block(Material.AIR, true);
        Block head = block(Material.AIR, true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(20, 69, 30)).thenReturn(ground);
        when(world.getBlockAt(20, 70, 30)).thenReturn(feet);
        when(world.getBlockAt(20, 71, 30)).thenReturn(head);
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(20, 69, 30, 21, 70, 31));
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN))
                .thenReturn(java.util.List.of(respawn));

        listener.onPlayerRespawn(event);

        ArgumentCaptor<Runnable> loadout = ArgumentCaptor.forClass(Runnable.class);
        verify(gameManager).scheduleRoundLater(
                org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(RoundPhase.ACTIVE),
                org.mockito.ArgumentMatchers.eq(1L),
                loadout.capture());
        loadout.getValue().run();
        verify(player.getInventory()).setHelmet(infectedHead);
        verify(roleEquipment).createHead();
        verify(gameManager).applyCurrentInfectedBuff(player);
    }

    @Test
    void headStartInfectedDeathReturnsToTheHoldingSpawnAndKeepsContainment() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);
        when(gameManager.claimHoldingRespawn(player)).thenReturn(true);
        Location holding = new Location(mock(World.class), 4.5, 80, 9.5);
        when(spawnRepository.loadedHoldingSpawn()).thenReturn(Optional.of(holding));

        listener.onPlayerRespawn(event);

        verify(spawnRepository, never()).loadedLocations(org.mockito.ArgumentMatchers.any());
        verify(event).setRespawnLocation(holding);
        verify(gameManager).scheduleRoundLater(
                org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(RoundPhase.HEADSTART),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void activeInfectedRespawnWithoutAPendingDeathIsIgnored() {
        when(gameManager.claimInfectedRespawn(player)).thenReturn(false);

        listener.onPlayerRespawn(event);

        verify(spawnRepository, never()).loadedLocations(org.mockito.ArgumentMatchers.any());
        verify(event, never()).setRespawnLocation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelsRoundWhenNoSafeDedicatedRespawnPointIsAvailable() {
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN)).thenReturn(java.util.List.of());

        listener.onPlayerRespawn(event);

        verify(gameManager).cancelForUnsafeInfectedRespawn();
        verify(event, never()).setRespawnLocation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unsafeRespawnCancellationUsesCapturedRoundLocationInsteadOfWorldSpawn() {
        Location captured = new Location(mock(World.class), 4064.5, 15, 2160.5);
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN)).thenReturn(java.util.List.of());
        when(gameManager.restoreAfterRoundRespawn(player))
                .thenReturn(Optional.empty(), Optional.of(captured));

        listener.onPlayerRespawn(event);

        verify(gameManager).cancelForUnsafeInfectedRespawn();
        verify(event).setRespawnLocation(captured);
    }

    @Test
    void eliminatedInfectedBecomesSpectatorDuringEndingWithoutARespawnLocation() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.ENDING);
        when(gameManager.isEliminatedInfected(player)).thenReturn(true);

        listener.onPlayerRespawn(event);

        ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(gameManager).scheduleCleanupLater(
                org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(1L),
                scheduled.capture());
        scheduled.getValue().run();

        verify(spawnRepository, never()).loadedLocations(org.mockito.ArgumentMatchers.any());
        verify(event, never()).setRespawnLocation(org.mockito.ArgumentMatchers.any());
        verify(player).setGameMode(org.bukkit.GameMode.SPECTATOR);
    }

    @Test
    void eliminatedInfectedDuringActivePlayReceivesADedicatedRespawnBeforeSpectating() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Location configured = new Location(world, 20.91, 70, 30.98, 45, 5);
        Block ground = block(Material.STONE, false);
        Block feet = block(Material.AIR, true);
        Block head = block(Material.AIR, true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(20, 69, 30)).thenReturn(ground);
        when(world.getBlockAt(20, 70, 30)).thenReturn(feet);
        when(world.getBlockAt(20, 71, 30)).thenReturn(head);
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(20, 69, 30, 21, 70, 31));
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN))
                .thenReturn(java.util.List.of(configured));
        when(gameManager.isEliminatedInfected(player)).thenReturn(true);

        listener.onPlayerRespawn(event);

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(event).setRespawnLocation(destination.capture());
        assertEquals(20.5, destination.getValue().getX());
        assertEquals(30.5, destination.getValue().getZ());
        verify(gameManager).scheduleRoundLater(
                org.mockito.ArgumentMatchers.eq(player),
                org.mockito.ArgumentMatchers.eq(RoundPhase.ACTIVE),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void roundCleanupRespawnUsesTheCapturedLocationAndSkipsZombieLoadout() {
        Location captured = new Location(mock(World.class), 4064.5, 15, 2160.5);
        when(gameManager.restoreAfterRoundRespawn(player)).thenReturn(Optional.of(captured));

        listener.onPlayerRespawn(event);

        verify(event).setRespawnLocation(captured);
        verify(spawnRepository, never()).loadedLocations(org.mockito.ArgumentMatchers.any());
        verify(gameManager, never()).claimInfectedRespawn(player);
        verify(gameManager, never()).scheduleRoundLater(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    private static Block block(Material material, boolean passable) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(passable);
        return block;
    }
}
