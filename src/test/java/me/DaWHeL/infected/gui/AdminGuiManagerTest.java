package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AdminGuiManagerTest {
    private GameManager gameManager;
    private AdminSetupService setupService;
    private AdminGuiManager manager;
    private Player administrator;

    @BeforeEach
    void setUp() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        gameManager = mock(GameManager.class);
        setupService = mock(AdminSetupService.class);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        manager = spy(new AdminGuiManager(plugin, gameManager, setupService));
        administrator = mock(Player.class);
        doNothing().when(manager).openMain(any(Player.class));
        doNothing().when(manager).openTeleportPoints(any(Player.class), anyInt());
        doNothing().when(manager).openPlayerActions(any(Player.class), any(UUID.class), anyInt());
    }

    @Test
    void pointSlotKeepsItsOriginalNameWhenSortedConfigurationChanges() {
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS, 2, Map.of(9, "north"));
        when(setupService.teleportPoints()).thenReturn(List.of(
                point("south", new AdminSetupService.StoredLocation("arena", 1, 64, 1, 0, 0))));

        manager.handleClick(administrator, holder, 9, ClickType.SHIFT_RIGHT);

        verify(administrator).sendMessage(contains("changed while the menu was open"));
        verify(manager).openTeleportPoints(administrator, 2);
        verify(setupService, never()).deleteTeleportPoint(anyString());
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
        when(setupService.teleportPoints()).thenReturn(List.of(
                point("north", new AdminSetupService.StoredLocation("arena", 20, 80, 20, 0, 0))));

        manager.handleClick(administrator, holder, AdminGuiLayout.CONFIRM, ClickType.LEFT);

        verify(setupService, never()).deleteTeleportPoint(anyString());
        verify(administrator).sendMessage(contains("point changed"));
        verify(manager).openTeleportPoints(administrator, 3);
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
    void cancelledTeleportReportsFailureAndKeepsTheSamePointPage() {
        AdminSetupService.StoredLocation stored =
                new AdminSetupService.StoredLocation("arena", 2, 64, 3, 0, 0);
        AdminMenuHolder holder = AdminMenuHolder.page(
                AdminMenuHolder.MenuType.TELEPORT_POINTS, 3, Map.of(9, "north"));
        when(setupService.teleportPoints()).thenReturn(List.of(point("north", stored)));
        when(administrator.teleport(any(Location.class))).thenReturn(false);
        World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);

            manager.handleClick(administrator, holder, 9, ClickType.LEFT);
        }

        verify(administrator).sendMessage(contains("cancelled"));
        verify(manager).openTeleportPoints(administrator, 3);
    }

    private static AdminSetupService.TeleportPoint point(
            String name,
            AdminSetupService.StoredLocation location
    ) {
        return new AdminSetupService.TeleportPoint(name, location);
    }
}
