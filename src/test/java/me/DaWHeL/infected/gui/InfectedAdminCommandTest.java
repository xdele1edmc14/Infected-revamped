package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRole;
import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InfectedAdminCommandTest {
    private AdminActionService actions;
    private AdminSetupService setupService;
    private AdminGuiNavigator navigator;
    private InfectedAdminCommand command;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        actions = mock(AdminActionService.class);
        setupService = mock(AdminSetupService.class);
        navigator = mock(AdminGuiNavigator.class);
        when(actions.setupChangesAllowed()).thenReturn(true);
        command = new InfectedAdminCommand(actions, setupService, navigator);
        bukkitCommand = mock(Command.class);
    }

    @Test
    void deniesPlayersWithoutAdminPermission() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(false);

        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[0]));

        verify(player).sendMessage(contains("permission"));
        verifyNoInteractions(navigator);
    }

    @Test
    void opensMainMenuForAuthorizedPlayer() {
        Player player = authorizedPlayer();

        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[0]));

        verify(navigator).openMain(player);
    }

    @Test
    void consoleReceivesCompactStatusAndHelp() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("infected.admin")).thenReturn(true);

        assertTrue(command.onCommand(console, bukkitCommand, "infected", new String[0]));

        verify(actions).status(console);
        verify(actions).help(console);
    }

    @Test
    void rootLifecycleSubcommandsDelegateToSharedActions() {
        Player player = authorizedPlayer();

        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"start"}));
        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"stop"}));
        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"reload"}));
        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"status"}));
        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"help"}));

        verify(actions).start(player);
        verify(actions).stop(player);
        verify(actions).reload(player);
        verify(actions).status(player);
        verify(actions).help(player);
    }

    @Test
    void addTeleportSubcommandStoresCurrentLocationAndRefreshesPointMenu() {
        Player player = authorizedPlayer();
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        assertTrue(command.onCommand(player, bukkitCommand, "infected",
                new String[]{"gui", "addteleport", "north"}));

        verify(setupService).saveTeleportPoint("north", location);
        verify(navigator).openTeleportPoints(player, 0);
    }

    @Test
    void roleAwareAddTeleportStoresAndReopensTheRequestedGroup() {
        Player player = authorizedPlayer();
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        assertTrue(command.onCommand(player, bukkitCommand, "infected",
                new String[]{"gui", "addteleport", "release", "north"}));

        verify(setupService).saveTeleportPoint(SpawnRole.INFECTED_RELEASE, "north", location);
        verify(navigator).openTeleportPoints(player, SpawnRole.INFECTED_RELEASE, 0);
    }

    @Test
    void addTeleportSubcommandRejectsLiveRoundMutation() {
        Player player = authorizedPlayer();
        when(actions.setupChangesAllowed()).thenReturn(false);

        assertTrue(command.onCommand(player, bukkitCommand, "infected",
                new String[]{"gui", "addteleport", "north"}));

        verifyNoInteractions(setupService, navigator);
        verify(player).sendMessage(contains("lobby"));
    }

    @Test
    void invalidSpawnRoleDoesNotWriteConfiguration() {
        Player player = authorizedPlayer();

        assertTrue(command.onCommand(player, bukkitCommand, "infected",
                new String[]{"gui", "addteleport", "unknown", "north"}));

        verify(player).sendMessage(contains("survivor|release|respawn"));
        verifyNoInteractions(setupService, navigator);
    }

    @Test
    void rejectsInvalidTeleportNameWithoutWritingConfiguration() {
        Player player = authorizedPlayer();

        assertTrue(command.onCommand(player, bukkitCommand, "infected",
                new String[]{"gui", "addteleport", "north.spawn"}));

        verify(player).sendMessage(contains("1-32 characters"));
        verifyNoInteractions(setupService, navigator);
    }

    @Test
    void completesOnlyTheSupportedGuiCommandPath() {
        Player player = authorizedPlayer();

        assertAll(
                () -> assertEquals(List.of("gui"),
                        command.onTabComplete(player, bukkitCommand, "infected", new String[]{"g"})),
                () -> assertEquals(List.of("start", "stop", "status"),
                        command.onTabComplete(player, bukkitCommand, "infected", new String[]{"st"})),
                () -> assertEquals(List.of("addteleport"),
                        command.onTabComplete(player, bukkitCommand, "infected", new String[]{"gui", "a"})),
                () -> assertEquals(List.of("survivor", "release", "respawn"),
                        command.onTabComplete(player, bukkitCommand, "infected",
                                new String[]{"gui", "addteleport", ""})),
                () -> assertEquals(List.of("<name>"),
                        command.onTabComplete(player, bukkitCommand, "infected",
                                new String[]{"gui", "addteleport", "release", ""}))
        );
    }

    private static Player authorizedPlayer() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        return player;
    }
}
