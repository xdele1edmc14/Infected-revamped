package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
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
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        spawnRepository = mock(SpawnRepository.class);
        listener = new InfectedRespawnListener(gameManager, spawnRepository);
        player = mock(Player.class);
        event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
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
        verify(spawnRepository, never()).loadedLocations(SpawnRole.SURVIVOR);
        verify(spawnRepository, never()).loadedLocations(SpawnRole.INFECTED_RELEASE);
    }

    @Test
    void headStartRespawnHandlingCannotReleaseAnInfected() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);

        listener.onPlayerRespawn(event);

        verify(spawnRepository, never()).randomLocation(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
    void eliminatedInfectedBecomesSpectatorDuringEndingWithoutARespawnLocation() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.ENDING);
        when(gameManager.isEliminatedInfected(player)).thenReturn(true);

        listener.onPlayerRespawn(event);

        ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(org.mockito.ArgumentMatchers.eq(plugin), scheduled.capture());
        scheduled.getValue().run();

        verify(spawnRepository, never()).loadedLocations(org.mockito.ArgumentMatchers.any());
        verify(event, never()).setRespawnLocation(org.mockito.ArgumentMatchers.any());
        verify(player).setGameMode(org.bukkit.GameMode.SPECTATOR);
    }

    private static Block block(Material material, boolean passable) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.isPassable()).thenReturn(passable);
        return block;
    }
}
