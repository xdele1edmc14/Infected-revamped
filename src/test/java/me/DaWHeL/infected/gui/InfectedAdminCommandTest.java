package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRole;
import me.DaWHeL.infected.RoundActionResult;
import me.DaWHeL.infected.TrackingCompassOverride;
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
    private GameManager gameManager;
    private AdminSetupService setupService;
    private AdminGuiNavigator navigator;
    private InfectedAdminCommand command;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        setupService = mock(AdminSetupService.class);
        navigator = mock(AdminGuiNavigator.class);
        command = new InfectedAdminCommand(gameManager, setupService, navigator);
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
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        when(gameManager.configuredStartingInfected()).thenReturn(2);
        when(setupService.snapshot(0, 0, 2)).thenReturn(
                new AdminSetupService.SetupSnapshot(true, 2, 2, 2, 6, 0, 2, 10, 5, 20));

        assertTrue(command.onCommand(console, bukkitCommand, "infected", new String[0]));

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(console, atLeast(3)).sendMessage(messages.capture());
        String combined = ChatColor.stripColor(String.join("\n", messages.getAllValues()));
        assertAll(
                () -> assertTrue(combined.contains("State: HEADSTART")),
                () -> assertTrue(combined.contains("Survivors: 0")),
                () -> assertTrue(combined.contains("Infected: 0")),
                () -> assertTrue(combined.contains("Setup: Ready")),
                () -> assertTrue(combined.contains("/infected"))
        );
        verify(setupService).snapshot(0, 0, 2);
        verify(setupService, never()).snapshot(0, 0);
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

        verify(player).sendMessage(contains("cannot be blank or contain periods"));
        verifyNoInteractions(setupService, navigator);
    }

    @Test
    void completesOnlyTheSupportedGuiCommandPath() {
        Player player = authorizedPlayer();

        assertAll(
                () -> assertEquals(List.of("gui"),
                        command.onTabComplete(player, bukkitCommand, "infected", new String[]{"g"})),
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

    @Test
    void adminCanForceTrackingCompassesOnAndReturnToAutomaticMode() {
        Player player = authorizedPlayer();
        when(gameManager.setTrackingCompassOverride(TrackingCompassOverride.ON))
                .thenReturn(RoundActionResult.accepted("Tracking compasses forced on."));
        when(gameManager.setTrackingCompassOverride(TrackingCompassOverride.AUTO))
                .thenReturn(RoundActionResult.accepted("Tracking compasses returned to automatic mode."));

        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"compass", "on"}));
        assertTrue(command.onCommand(player, bukkitCommand, "infected", new String[]{"compass", "auto"}));

        verify(gameManager).setTrackingCompassOverride(TrackingCompassOverride.ON);
        verify(gameManager).setTrackingCompassOverride(TrackingCompassOverride.AUTO);
    }

    @Test
    void consoleCanControlTrackingCompassesAndInspectStatus() {
        CommandSender console = mock(CommandSender.class);
        when(gameManager.setTrackingCompassOverride(TrackingCompassOverride.OFF))
                .thenReturn(RoundActionResult.accepted("Tracking compasses forced off."));
        when(gameManager.trackingCompassOverride()).thenReturn(TrackingCompassOverride.OFF);

        assertTrue(command.onCommand(console, bukkitCommand, "infected", new String[]{"compass", "off"}));
        assertTrue(command.onCommand(console, bukkitCommand, "infected", new String[]{"compass", "status"}));

        verify(gameManager).setTrackingCompassOverride(TrackingCompassOverride.OFF);
        verify(console, atLeastOnce()).sendMessage(contains("forced off"));
        verify(console, atLeastOnce()).sendMessage(contains("Tracking compasses"));
    }

    @Test
    void compassSubcommandsAreTabCompleted() {
        Player player = authorizedPlayer();

        assertEquals(List.of("compass"),
                command.onTabComplete(player, bukkitCommand, "infected", new String[]{"c"}));
        assertEquals(List.of("on", "off"),
                command.onTabComplete(player, bukkitCommand, "infected", new String[]{"compass", "o"}));
    }

    private static Player authorizedPlayer() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        return player;
    }
}
