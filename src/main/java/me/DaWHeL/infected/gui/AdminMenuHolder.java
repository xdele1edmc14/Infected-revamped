package me.DaWHeL.infected.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;

public final class AdminMenuHolder implements InventoryHolder {
    private final MenuType type;
    private final int page;
    private final String target;
    private final ConfirmationAction confirmationAction;
    private final Map<Integer, String> slotTargets;
    private final String expectedState;
    private Inventory inventory;

    private AdminMenuHolder(
            MenuType type,
            int page,
            String target,
            ConfirmationAction confirmationAction,
            Map<Integer, String> slotTargets,
            String expectedState
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.page = Math.max(0, page);
        this.target = target;
        this.confirmationAction = confirmationAction;
        this.slotTargets = Map.copyOf(slotTargets);
        this.expectedState = expectedState;
    }

    public static AdminMenuHolder root(MenuType type) {
        return new AdminMenuHolder(type, 0, null, null, Map.of(), null);
    }

    public static AdminMenuHolder page(MenuType type, int page) {
        return page(type, page, Map.of());
    }

    public static AdminMenuHolder page(MenuType type, int page, Map<Integer, String> slotTargets) {
        return new AdminMenuHolder(type, page, null, null,
                Objects.requireNonNull(slotTargets, "slotTargets"), null);
    }

    public static AdminMenuHolder playerActions(UUID playerId, int returnPage) {
        return playerActions(playerId, returnPage, null);
    }

    public static AdminMenuHolder playerActions(UUID playerId, int returnPage, String expectedTeam) {
        return new AdminMenuHolder(MenuType.PLAYER_ACTIONS, returnPage,
                Objects.requireNonNull(playerId, "playerId").toString(), null, Map.of(), expectedTeam);
    }

    public static AdminMenuHolder confirmation(ConfirmationAction action, String target, int returnPage) {
        return confirmation(action, target, null, returnPage);
    }

    public static AdminMenuHolder confirmation(
            ConfirmationAction action,
            String target,
            String expectedState,
            int returnPage
    ) {
        return new AdminMenuHolder(MenuType.CONFIRMATION, returnPage, target,
                Objects.requireNonNull(action, "action"), Map.of(), expectedState);
    }

    void bind(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Menu holder is already bound to an inventory.");
        }
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public MenuType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public String target() {
        return target;
    }

    public ConfirmationAction confirmationAction() {
        return confirmationAction;
    }

    public String slotTarget(int rawSlot) {
        return slotTargets.get(rawSlot);
    }

    public String expectedState() {
        return expectedState;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Menu inventory has not been created yet.");
    }

    public enum MenuType {
        MAIN,
        LIVE_STATUS,
        TELEPORT_POINTS,
        SETUP_STATUS,
        PLAYERS,
        PLAYER_ACTIONS,
        HELP,
        CONFIRMATION
    }

    public enum ConfirmationAction {
        START,
        STOP,
        CLEAR_INFECTED_SPAWN,
        DELETE_TELEPORT_POINT
    }
}
