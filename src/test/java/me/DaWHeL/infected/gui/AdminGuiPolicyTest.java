package me.DaWHeL.infected.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdminGuiPolicyTest {
    @Test
    void onlyAllowsStartWhenSetupIsReadyAndEventIsStopped() {
        AdminSetupService.SetupSnapshot ready = snapshot(true, 2);
        AdminSetupService.SetupSnapshot incomplete = snapshot(false, 0);

        assertAll(
                () -> assertTrue(AdminGuiPolicy.canStart(ready, false)),
                () -> assertFalse(AdminGuiPolicy.canStart(ready, true)),
                () -> assertFalse(AdminGuiPolicy.canStart(incomplete, false))
        );
    }

    @Test
    void computesAndClampsPagesAtListBoundaries() {
        assertAll(
                () -> assertEquals(1, AdminGuiPolicy.pageCount(0, 28)),
                () -> assertEquals(1, AdminGuiPolicy.pageCount(28, 28)),
                () -> assertEquals(2, AdminGuiPolicy.pageCount(29, 28)),
                () -> assertEquals(0, AdminGuiPolicy.clampPage(-2, 29, 28)),
                () -> assertEquals(1, AdminGuiPolicy.clampPage(8, 29, 28))
        );
    }

    @Test
    void distinguishesTopInventorySlotsFromPlayerInventorySlots() {
        assertAll(
                () -> assertFalse(AdminGuiPolicy.isTopInventoryClick(-999, 54)),
                () -> assertTrue(AdminGuiPolicy.isTopInventoryClick(0, 54)),
                () -> assertTrue(AdminGuiPolicy.isTopInventoryClick(53, 54)),
                () -> assertFalse(AdminGuiPolicy.isTopInventoryClick(54, 54))
        );
    }

    @Test
    void holderFactoriesKeepTypedContextWithoutAInventoryTitle() {
        UUID playerId = UUID.randomUUID();
        AdminMenuHolder points = AdminMenuHolder.page(AdminMenuHolder.MenuType.TELEPORT_POINTS, 3);
        AdminMenuHolder player = AdminMenuHolder.playerActions(playerId, 2);
        AdminMenuHolder confirmation = AdminMenuHolder.confirmation(
                AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT, "north", 1);

        assertAll(
                () -> assertEquals(AdminMenuHolder.MenuType.TELEPORT_POINTS, points.type()),
                () -> assertEquals(3, points.page()),
                () -> assertEquals(playerId.toString(), player.target()),
                () -> assertEquals(2, player.page()),
                () -> assertEquals(AdminMenuHolder.MenuType.CONFIRMATION, confirmation.type()),
                () -> assertEquals(AdminMenuHolder.ConfirmationAction.DELETE_TELEPORT_POINT,
                        confirmation.confirmationAction()),
                () -> assertEquals("north", confirmation.target()),
                () -> assertEquals(1, confirmation.page())
        );
    }

    @Test
    void rejectsInvalidPageSizes() {
        assertThrows(IllegalArgumentException.class, () -> AdminGuiPolicy.pageCount(1, 0));
    }

    private static AdminSetupService.SetupSnapshot snapshot(boolean spawn, int points) {
        return new AdminSetupService.SetupSnapshot(spawn, points, 4, 1, 2, 10, 5);
    }
}
