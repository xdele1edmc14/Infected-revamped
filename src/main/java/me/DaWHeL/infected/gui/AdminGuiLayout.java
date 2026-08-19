package me.DaWHeL.infected.gui;

public final class AdminGuiLayout {
    public static final int MAIN_SIZE = 36;
    public static final int STATUS = 11;
    public static final int INFECTED_SPAWN = 12;
    public static final int TELEPORT_POINTS = 13;
    public static final int SETUP_STATUS = 14;
    public static final int EVENT_PLAYERS = 15;
    public static final int START_EVENT = 20;
    public static final int STOP_EVENT = 22;
    public static final int RELOAD_CONFIG = 24;
    public static final int MAIN_BACK = 27;
    public static final int QUICK_HELP = 34;
    public static final int MAIN_CLOSE = 35;

    public static final int SURVIVOR_SPAWNS = 20;
    public static final int INFECTED_RELEASE_SPAWNS = 22;
    public static final int INFECTED_RESPAWN_SPAWNS = 24;

    public static final int BACK = 45;
    public static final int PREVIOUS_PAGE = 46;
    public static final int CLOSE = 49;
    public static final int NEXT_PAGE = 52;
    public static final int HELP = 53;

    public static final int CONFIRM = 11;
    public static final int CONFIRMATION_SUMMARY = 13;
    public static final int CANCEL = 15;

    public static final int PAGE_SIZE = 36;
    private static final int FIRST_CONTENT_SLOT = 9;
    private static final int LAST_CONTENT_SLOT = 44;

    private AdminGuiLayout() {
    }

    public static int contentSlot(int offset) {
        if (offset < 0 || offset >= PAGE_SIZE) {
            throw new IllegalArgumentException("Content offset must be between 0 and 35.");
        }
        return FIRST_CONTENT_SLOT + offset;
    }

    public static int contentOffset(int rawSlot) {
        if (rawSlot < FIRST_CONTENT_SLOT || rawSlot > LAST_CONTENT_SLOT) {
            return -1;
        }
        return rawSlot - FIRST_CONTENT_SLOT;
    }
}
