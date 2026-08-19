package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.RoundPhase;

import java.util.Objects;

public final class AdminGuiPolicy {
    private AdminGuiPolicy() {
    }

    public static boolean canStart(AdminSetupService.SetupSnapshot snapshot, boolean gameRunning) {
        return !gameRunning && snapshot.ready();
    }

    public static boolean canStop(RoundPhase phase) {
        return phase == RoundPhase.COUNTDOWN
                || phase == RoundPhase.HEADSTART
                || phase == RoundPhase.ACTIVE;
    }

    public static int pageCount(int itemCount, int pageSize) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("Item count cannot be negative.");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive.");
        }
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    public static int clampPage(int requestedPage, int itemCount, int pageSize) {
        return Math.max(0, Math.min(requestedPage, pageCount(itemCount, pageSize) - 1));
    }

    public static boolean isTopInventoryClick(int rawSlot, int topSize) {
        return rawSlot >= 0 && rawSlot < topSize;
    }

    public static boolean matchesExpected(String expected, String current) {
        return expected != null && Objects.equals(expected, current);
    }
}
