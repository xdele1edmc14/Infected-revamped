package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import me.DaWHeL.infected.TeleportManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnCommandTest {

    @Test
    void addAndRemoveCommandsTargetTheExplicitSpawnRole() {
        TeleportManager teleports = mock(TeleportManager.class);
        Player player = mock(Player.class);
        when(teleports.addTeleportPoint(player, SpawnRole.INFECTED_RELEASE, "north")).thenReturn(true);
        when(teleports.removeTeleportPoint(SpawnRole.INFECTED_RESPAWN, "south")).thenReturn(true);

        new AddTeleportCommand(teleports).onCommand(
                player, mock(Command.class), "addteleport", new String[]{"release", "north"});
        new RemoveTeleportCommand(teleports).onCommand(
                player, mock(Command.class), "removeteleport", new String[]{"respawn", "south"});

        verify(teleports).addTeleportPoint(player, SpawnRole.INFECTED_RELEASE, "north");
        verify(teleports).removeTeleportPoint(SpawnRole.INFECTED_RESPAWN, "south");
    }

    @Test
    void listCommandReadsRoleSpecificSpawnRepositoryRecords() {
        SpawnRepository repository = mock(SpawnRepository.class);
        CommandSender sender = mock(CommandSender.class);
        when(repository.points(SpawnRole.INFECTED_RELEASE)).thenReturn(List.of(
                new SpawnRepository.NamedSpawn("north",
                        new SpawnRepository.StoredSpawn("arena", 2.5, 70, 9.5, 45, 0))));

        new ListTeleportPoints(repository).onCommand(
                sender, mock(Command.class), "listteleportpoints", new String[]{"release"});

        verify(sender).sendMessage(contains("north"));
        verify(sender).sendMessage(contains("arena"));
    }

    @Test
    void teleportPointCommandUsesTheExactRepositoryLocationAndReportsCancellation() {
        SpawnRepository repository = mock(SpawnRepository.class);
        Player player = mock(Player.class);
        Location destination = new Location(mock(World.class), 2.25, 70.5, 9.75, 45, 3);
        when(repository.loadedPoint(SpawnRole.SURVIVOR, "north")).thenReturn(Optional.of(destination));
        when(player.teleport(destination)).thenReturn(false);

        new TeleportToTeleportPoint(repository).onCommand(
                player, mock(Command.class), "tttp", new String[]{"survivor", "north"});

        verify(player).teleport(destination);
        verify(player).sendMessage(contains("cancelled"));
    }

    @Test
    void holdingSpawnTeleportReportsCancellationInsteadOfClaimingSuccess() {
        SpawnRepository repository = mock(SpawnRepository.class);
        Player player = mock(Player.class);
        Location destination = new Location(mock(World.class), 4.5, 80, 4.5);
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(destination));
        when(player.teleport(destination)).thenReturn(false);

        new TpInfectedSpawn(repository).onCommand(
                player, mock(Command.class), "tpinfectedspawn", new String[0]);

        verify(player).teleport(destination);
        verify(player).sendMessage(contains("cancelled"));
    }
}
