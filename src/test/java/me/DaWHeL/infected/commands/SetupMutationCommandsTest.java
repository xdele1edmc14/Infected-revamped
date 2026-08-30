package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.TeleportManager;
import me.DaWHeL.infected.gui.AdminSetupService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupMutationCommandsTest {

    @Test
    void addAndRemoveTeleportRejectLiveRoundMutation() {
        GameManager gameManager = mock(GameManager.class);
        TeleportManager teleports = mock(TeleportManager.class);
        Player player = mock(Player.class);
        CommandSender sender = mock(CommandSender.class);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);

        new AddTeleportCommand(teleports, gameManager).onCommand(
                player, mock(Command.class), "addteleport", new String[]{"north"});
        new RemoveTeleportCommand(teleports, gameManager).onCommand(
                sender, mock(Command.class), "removeteleport", new String[]{"north"});

        verify(teleports, never()).addTeleportPoint(player, "north");
        verify(teleports, never()).removeTeleportPoint("north");
        verify(player).sendMessage(contains("lobby"));
        verify(sender).sendMessage(contains("lobby"));
    }

    @Test
    void holdingSpawnCommandRejectsLiveRoundMutation() {
        GameManager gameManager = mock(GameManager.class);
        AdminSetupService setup = mock(AdminSetupService.class);
        Player player = mock(Player.class);
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);

        new CreateInfectedSpawn(gameManager, setup).onCommand(
                player, mock(Command.class), "createinfectedspawn", new String[0]);

        verify(setup, never()).setInfectedSpawn(org.mockito.ArgumentMatchers.any(Location.class));
        verify(player).sendMessage(contains("lobby"));
    }
}
