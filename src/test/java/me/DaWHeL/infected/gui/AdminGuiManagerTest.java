package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundActionResult;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRole;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AdminGuiManagerTest {
    private GameManager gameManager;
    private InfectedPlugin plugin;
    private AdminSetupService setupService;
    private AdminGuiManager manager;
    private Player administrator;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        gameManager = mock(GameManager.class);
        setupService = mock(AdminSetupService.class);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        manager = spy(new AdminGuiManager(plugin, gameManager, setupService));
        administrator = mock(Player.class);
        doNothing().when(manager).openMain(any(Player.class));
        doNothing().when(manager).openTeleportRoles(any(Player.class));
        doNothing().when(manager).openTeleportPoints(any(Player.class), anyInt());
        doNothing().when(manager).openTeleportPoints(any(Player.class), any(SpawnRole.class), anyInt());
        doNothing().when(manager).openPlayers(any(Player.class), anyInt());
        doNothing().when(manager).openPlayerActions(any(Player.class), any(UUID.class), anyInt());
    }

    @Test
    void mainTeleportControlOpensTheSpawnRoleSelector() {
        manager.handleClick(administrator, AdminMenuHolder.root(AdminMenuHolder.MenuType.MAIN),
                AdminGuiLayout.TELEPORT_POINTS, ClickType.LEFT);

        verify(manager).openTeleportRoles(administrator);
        verify(manager, never()).openTeleportPoints(eq(administrator), anyInt());
    }

    @Test
    void roleSelectorRoutesEachCenteredControlToItsOwnSpawnGroup() {
        AdminMenuHolder holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.TELEPORT_ROLES);

        manager.handleClick(administrator, holder, AdminGuiLayout.SURVIVOR_SPAWNS, ClickType.LEFT);
        manager.handleClick(administrator, holder, AdminGuiLayout.INFECTED_RELEASE_SPAWNS, ClickType.LEFT);
        manager.handleClick(administrator, holder, AdminGuiLayout.INFECTED_RESPAWN_SPAWNS, ClickType.LEFT);

        verify(manager).openTeleportPoints(administrator, SpawnRole.SURVIVOR, 0);
        verify(manager).openTeleportPoints(administrator, SpawnRole.INFECTED_RELEASE, 0);
        verify(manager).openTeleportPoints(administrator, SpawnRole.INFECTED_RESPAWN, 0);
    }

    @Test
    void pointSelectionReadsOnlyTheRoleStoredInTheMenu() {
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS,
                0,
                Map.of(9, "release-north"),
                SpawnRole.INFECTED_RELEASE);
        when(setupService.teleportPoints(SpawnRole.INFECTED_RELEASE)).thenReturn(List.of(
                point("release-north", new AdminSetupService.StoredLocation("release", 8, 70, 9, 0, 0))));
        when(administrator.teleport(any(Location.class))).thenReturn(true);
        World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("release")).thenReturn(world);
            manager.handleClick(administrator, holder, 9, ClickType.LEFT);
        }

        verify(setupService).teleportPoints(SpawnRole.INFECTED_RELEASE);
        verify(administrator).teleport(any(Location.class));
        verify(setupService, never()).teleportPoints();
    }

    @Test
    void deleteConfirmationCannotUseASameNamePointFromAnotherRole() {
        AdminSetupService.StoredLocation survivorLocation =
                new AdminSetupService.StoredLocation("arena", 4, 70, 4, 0, 0);
        AdminMenuHolder holder = AdminMenuHolder.confirmation(
                AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT,
                "north",
                expectedState(survivorLocation),
                2,
                SpawnRole.INFECTED_RESPAWN);
        when(setupService.teleportPoints()).thenReturn(List.of(point("north", survivorLocation)));
        when(setupService.teleportPoints(SpawnRole.INFECTED_RESPAWN)).thenReturn(List.of());

        manager.handleClick(administrator, holder, AdminGuiLayout.CONFIRM, ClickType.LEFT);

        verify(setupService, never()).deleteTeleportPoint(anyString());
        verify(setupService, never()).deleteTeleportPoint(any(SpawnRole.class), anyString());
        verify(manager).openTeleportPoints(administrator, SpawnRole.INFECTED_RESPAWN, 2);
    }

    @Test
    void pointSlotKeepsItsOriginalNameWhenSortedConfigurationChanges() {
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS, 0, Map.of(9, "north"));
        when(setupService.teleportPoints(SpawnRole.SURVIVOR)).thenReturn(List.of(
                point("alpha", new AdminSetupService.StoredLocation("arena", 1, 64, 1, 0, 0)),
                point("north", new AdminSetupService.StoredLocation("arena", 20, 70, 20, 0, 0))));
        when(administrator.teleport(any(Location.class))).thenReturn(true);
        World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);

            manager.handleClick(administrator, holder, 9, ClickType.LEFT);
        }

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(administrator).teleport(destination.capture());
        org.junit.jupiter.api.Assertions.assertEquals(20.5, destination.getValue().getX());
        org.junit.jupiter.api.Assertions.assertEquals(71.0, destination.getValue().getY());
        org.junit.jupiter.api.Assertions.assertEquals(20.5, destination.getValue().getZ());
    }

    @Test
    void changedSpawnIsNotClearedByStaleConfirmation() {
        AdminMenuHolder holder = AdminMenuHolder.confirmation(
                AdminMenuHolder.ConfirmationAction.CLEAR_INFECTED_SPAWN,
                null, "previous-location", 0);
        when(setupService.infectedSpawn()).thenReturn(Optional.of(
                new AdminSetupService.StoredLocation("arena", 4, 70, 4, 0, 0)));

        manager.handleClick(administrator, holder, AdminGuiLayout.CONFIRM, ClickType.LEFT);

        verify(setupService, never()).clearInfectedSpawn();
        verify(administrator).sendMessage(contains("spawn changed"));
        verify(manager).openMain(administrator);
    }

    @Test
    void recreatedPointIsNotDeletedByStaleConfirmation() {
        AdminMenuHolder holder = AdminMenuHolder.confirmation(
                AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT,
                "north", "previous-location", 3);
        when(setupService.teleportPoints(SpawnRole.SURVIVOR)).thenReturn(List.of(
                point("north", new AdminSetupService.StoredLocation("arena", 20, 80, 20, 0, 0))));

        manager.handleClick(administrator, holder, AdminGuiLayout.CONFIRM, ClickType.LEFT);

        verify(setupService, never()).deleteTeleportPoint(anyString());
        verify(administrator).sendMessage(contains("point changed"));
        verify(manager).openTeleportPoints(administrator, SpawnRole.SURVIVOR, 3);
    }

    @Test
    void changedTeamIsReviewedInsteadOfToggledAgain() {
        UUID targetId = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        when(target.isOnline()).thenReturn(true);
        Survivor survivor = mock(Survivor.class);
        when(survivor.getPlayer()).thenReturn(target);
        when(gameManager.getSurvivors()).thenReturn(List.of(survivor));
        AdminMenuHolder holder = AdminMenuHolder.playerActions(targetId, 1, "infected");

        manager.handleClick(administrator, holder, 21, ClickType.LEFT);

        verify(gameManager, never()).toggleZombie(any(Player.class));
        verify(administrator).sendMessage(contains("team changed"));
        verify(manager).openPlayerActions(administrator, targetId, 1);
    }

    @Test
    void playerActionReportsSafeToggleRejectionInsteadOfClaimingSuccess() {
        UUID targetId = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        when(target.isOnline()).thenReturn(true);
        Survivor survivor = mock(Survivor.class);
        when(survivor.getPlayer()).thenReturn(target);
        when(gameManager.getSurvivors()).thenReturn(List.of(survivor));
        when(gameManager.toggleZombieSafely(target)).thenReturn(
                RoundActionResult.rejected("Zombie toggles are only allowed during active play."));
        AdminMenuHolder holder = AdminMenuHolder.playerActions(targetId, 0, "survivor");

        manager.handleClick(administrator, holder, 21, ClickType.LEFT);

        verify(gameManager).toggleZombieSafely(target);
        verify(administrator).sendMessage(contains("only allowed during active play"));
        verify(gameManager, never()).toggleZombie(target);
    }

    @Test
    void playerActionDirectsAdminsToTheSafeCommandWithoutImmediateRemoval() {
        UUID targetId = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Target");
        when(target.isOnline()).thenReturn(true);
        Survivor survivor = mock(Survivor.class);
        when(survivor.getPlayer()).thenReturn(target);
        when(gameManager.getSurvivors()).thenReturn(List.of(survivor));
        AdminMenuHolder holder = AdminMenuHolder.playerActions(targetId, 2, "survivor");

        manager.handleClick(administrator, holder, 23, ClickType.LEFT);

        verify(gameManager, never()).removePlayer(any(Player.class));
        verify(administrator).sendMessage(contains("/removeplayer Target"));
    }

    @Test
    void guiReloadIsRejectedOutsideTheLobby() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);

        manager.handleClick(administrator, AdminMenuHolder.root(AdminMenuHolder.MenuType.MAIN),
                AdminGuiLayout.RELOAD_CONFIG, ClickType.LEFT);

        verify(plugin, never()).reloadConfig();
        verify(administrator).sendMessage(contains("only be reloaded in the lobby"));
    }

    @Test
    void cancelledTeleportReportsFailureAndKeepsTheSamePointPage() {
        AdminSetupService.StoredLocation stored =
                new AdminSetupService.StoredLocation("arena", 2, 64, 3, 0, 0);
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS, 3, Map.of(9, "north"));
        when(setupService.teleportPoints(SpawnRole.SURVIVOR)).thenReturn(List.of(point("north", stored)));
        when(administrator.teleport(any(Location.class))).thenReturn(false);
        World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);

            manager.handleClick(administrator, holder, 9, ClickType.LEFT);
        }

        verify(administrator).sendMessage(contains("cancelled"));
        verify(manager).openTeleportPoints(administrator, SpawnRole.SURVIVOR, 3);
    }

    private static AdminSetupService.TeleportPoint point(
            String name,
            AdminSetupService.StoredLocation location
    ) {
        return new AdminSetupService.TeleportPoint(name, location);
    }

    private static String expectedState(AdminSetupService.StoredLocation location) {
        return location.world()
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.x()))
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.y()))
                + '|' + Long.toUnsignedString(Double.doubleToLongBits(location.z()))
                + '|' + Integer.toUnsignedString(Float.floatToIntBits(location.yaw()))
                + '|' + Integer.toUnsignedString(Float.floatToIntBits(location.pitch()));
    }
}
