package me.DaWHeL.infected.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminGuiLayoutTest {
    @Test
    void mainControlsUseTheRequestedSlots() {
        assertAll(
                () -> assertEquals(4, AdminGuiLayout.STATUS),
                () -> assertEquals(10, AdminGuiLayout.INFECTED_SPAWN),
                () -> assertEquals(12, AdminGuiLayout.TELEPORT_POINTS),
                () -> assertEquals(14, AdminGuiLayout.SETUP_STATUS),
                () -> assertEquals(16, AdminGuiLayout.EVENT_PLAYERS),
                () -> assertEquals(28, AdminGuiLayout.START_EVENT),
                () -> assertEquals(30, AdminGuiLayout.STOP_EVENT),
                () -> assertEquals(32, AdminGuiLayout.RELOAD_CONFIG),
                () -> assertEquals(40, AdminGuiLayout.QUICK_HELP),
                () -> assertEquals(45, AdminGuiLayout.BACK),
                () -> assertEquals(49, AdminGuiLayout.CLOSE),
                () -> assertEquals(53, AdminGuiLayout.HELP)
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
