package me.DaWHeL.infected.gui.weapon;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WeaponMenuHolder implements InventoryHolder {
    private final MenuType type;
    private final int page;
    private final UUID target;
    private final String expectedState;
    private final Map<Integer, UUID> slotTargets;
    private Inventory inventory;

    public WeaponMenuHolder(MenuType type, int page, UUID target, String expectedState,
                            Map<Integer, UUID> slotTargets) {
        this.type = Objects.requireNonNull(type, "type");
        this.page = Math.max(0, page);
        this.target = target;
        this.expectedState = expectedState;
        this.slotTargets = Map.copyOf(slotTargets);
    }

    public static WeaponMenuHolder root(MenuType type) { return new WeaponMenuHolder(type, 0, null, null, Map.of()); }
    public static WeaponMenuHolder page(MenuType type, int page, Map<Integer, UUID> targets) {
        return new WeaponMenuHolder(type, page, null, null, targets);
    }
    public static WeaponMenuHolder target(MenuType type, UUID target, int page) {
        return new WeaponMenuHolder(type, page, target, null, Map.of());
    }
    public static WeaponMenuHolder confirmation(MenuType type, String expectedState) {
        return new WeaponMenuHolder(type, 0, null, expectedState, Map.of());
    }
    public void bind(Inventory inventory) { this.inventory = Objects.requireNonNull(inventory, "inventory"); }
    public MenuType type() { return type; }
    public int page() { return page; }
    public UUID target() { return target; }
    public String expectedState() { return expectedState; }
    public UUID slotTarget(int slot) { return slotTargets.get(slot); }
    @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory, "Menu is not bound."); }

    public enum MenuType { WIZARD, GUNS, GUN_EDITOR, GRENADES, GRENADE_EDITOR, CONFIRM_FILL, CONFIRM_CLEAR,
        CONFIRM_DELETE_GUN, CONFIRM_DELETE_GRENADE }
}
