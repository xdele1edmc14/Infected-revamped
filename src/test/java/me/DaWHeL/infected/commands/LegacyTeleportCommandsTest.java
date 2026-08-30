package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyTeleportCommandsTest {

    @Test
    void listReadsRoleSpecificSurvivorPointsAtStoredPrecision() {
        SpawnRepository repository = mock(SpawnRepository.class);
        CommandSender sender = mock(CommandSender.class);
        when(repository.points(SpawnRole.SURVIVOR)).thenReturn(List.of(
                new SpawnRepository.NamedSpawn("north",
                        new SpawnRepository.StoredSpawn("arena", 2.25, 70.5, -4.75, 90, 5))));

        new ListTeleportPoints(repository).onCommand(sender, mock(Command.class), "listteleportpoints",
                new String[0]);

        verify(sender).sendMessage(contains("north"));
        verify(sender).sendMessage(contains("2.25"));
        verify(sender).sendMessage(contains("70.5"));
        verify(sender).sendMessage(contains("-4.75"));
    }

    @Test
    void tttpUsesExactRoleSpecificLocationAndReportsCancellation() {
        SpawnRepository repository = mock(SpawnRepository.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location stored = new Location(world, 2.25, 70.5, -4.75, 90, 5);
        when(repository.loadedLocation(SpawnRole.SURVIVOR, "north")).thenReturn(Optional.of(stored));
        when(player.teleport(stored)).thenReturn(false);

        new TeleportToTeleportPoint(repository).onCommand(
                player, mock(Command.class), "tttp", new String[]{"north"});

        verify(player).teleport(stored);
        verify(player).sendMessage(contains("cancelled"));
        verify(player, never()).sendMessage(contains("Teleported to north"));
    }

    @Test
    void infectedSpawnTeleportReportsCancellationInsteadOfSuccess() {
        SpawnRepository repository = mock(SpawnRepository.class);
        Player player = mock(Player.class);
        Location stored = mock(Location.class);
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(stored));
        when(player.teleport(stored)).thenReturn(false);

        new TpInfectedSpawn(repository).onCommand(
                player, mock(Command.class), "tpinfectedspawn", new String[0]);

        verify(player).teleport(eq(stored));
        verify(player).sendMessage(contains("cancelled"));
        verify(player, never()).sendMessage(contains("Teleported to infected spawn"));
    }
}
