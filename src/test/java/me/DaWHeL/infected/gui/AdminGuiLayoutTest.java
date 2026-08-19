package me.DaWHeL.infected.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminGuiLayoutTest {
    @Test
    void mainControlsUseTheRequestedSlots() {
        assertAll(
                () -> assertEquals(36, AdminGuiLayout.MAIN_SIZE),
                () -> assertEquals(11, AdminGuiLayout.STATUS),
                () -> assertEquals(12, AdminGuiLayout.INFECTED_SPAWN),
                () -> assertEquals(13, AdminGuiLayout.TELEPORT_POINTS),
                () -> assertEquals(14, AdminGuiLayout.SETUP_STATUS),
                () -> assertEquals(15, AdminGuiLayout.EVENT_PLAYERS),
                () -> assertEquals(20, AdminGuiLayout.START_EVENT),
                () -> assertEquals(22, AdminGuiLayout.STOP_EVENT),
                () -> assertEquals(24, AdminGuiLayout.RELOAD_CONFIG),
                () -> assertEquals(27, AdminGuiLayout.MAIN_BACK),
                () -> assertEquals(34, AdminGuiLayout.QUICK_HELP),
                () -> assertEquals(35, AdminGuiLayout.MAIN_CLOSE)
        );
    }

    @Test
    void confirmationControlsUseTheRequestedSlots() {
        assertAll(
                () -> assertEquals(11, AdminGuiLayout.CONFIRM),
                () -> assertEquals(13, AdminGuiLayout.CONFIRMATION_SUMMARY),
                () -> assertEquals(15, AdminGuiLayout.CANCEL)
        );
    }

    @Test
    void spawnRoleControlsAreCentered() {
        assertAll(
                () -> assertEquals(20, AdminGuiLayout.SURVIVOR_SPAWNS),
                () -> assertEquals(22, AdminGuiLayout.INFECTED_RELEASE_SPAWNS),
                () -> assertEquals(24, AdminGuiLayout.INFECTED_RESPAWN_SPAWNS)
        );
    }

    @Test
    void pagedContentMapsOnlyTheFourReadableMiddleRows() {
        assertAll(
                () -> assertEquals(36, AdminGuiLayout.PAGE_SIZE),
                () -> assertEquals(9, AdminGuiLayout.contentSlot(0)),
                () -> assertEquals(44, AdminGuiLayout.contentSlot(35)),
                () -> assertEquals(0, AdminGuiLayout.contentOffset(9)),
                () -> assertEquals(35, AdminGuiLayout.contentOffset(44)),
                () -> assertEquals(-1, AdminGuiLayout.contentOffset(8)),
                () -> assertEquals(-1, AdminGuiLayout.contentOffset(45))
        );
    }
}
