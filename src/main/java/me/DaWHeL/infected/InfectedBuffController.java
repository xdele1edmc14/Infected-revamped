package me.DaWHeL.infected;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class InfectedBuffController {
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;
    static final EffectProfile ENABLED_EFFECTS = new EffectProfile(1, true);
    static final EffectProfile DISABLED_EFFECTS = new EffectProfile(0, false);
    private final NamespacedKey compassKey;
    private final Supplier<ItemStack> compassFactory;
    private final BiConsumer<Player, EffectProfile> effectApplicator;

    public InfectedBuffController(Plugin plugin) {
        this(new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "tracking_compass"),
                () -> new ItemStack(Material.COMPASS),
                InfectedBuffController::applyEffects);
    }

    InfectedBuffController(
            NamespacedKey compassKey,
            Supplier<ItemStack> compassFactory,
            BiConsumer<Player, EffectProfile> effectApplicator
    ) {
        this.compassKey = Objects.requireNonNull(compassKey, "compassKey");
        this.compassFactory = Objects.requireNonNull(compassFactory, "compassFactory");
        this.effectApplicator = Objects.requireNonNull(effectApplicator, "effectApplicator");
    }

    public void apply(Player player, boolean enabled) {
        Objects.requireNonNull(player, "player");
        effectApplicator.accept(player, enabled ? ENABLED_EFFECTS : DISABLED_EFFECTS);

        if (enabled) {
            ensureOwnedCompass(player);
        } else {
            removeOwnedCompasses(player);
        }
    }

    public void removeOwnedCompasses(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isOwnedCompass(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
    }

    public void clearRoleState(Player player) {
        Objects.requireNonNull(player, "player");
        removeOwnedCompasses(player);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
    }

    public boolean isOwnedCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(compassKey, PersistentDataType.BYTE);
    }

    private void ensureOwnedCompass(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isOwnedCompass(item)) {
                return;
            }
        }

        ItemStack compass = compassFactory.get();
        ItemMeta meta = Objects.requireNonNull(compass.getItemMeta(), "Compass metadata");
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);
    }

    private static PotionEffect effect(PotionEffectType type, int amplifier) {
        return new PotionEffect(type, PERMANENT_DURATION, amplifier, false, false, true);
    }

    private static void applyEffects(Player player, EffectProfile profile) {
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.addPotionEffect(effect(PotionEffectType.SPEED, profile.speedAmplifier()));
        if (profile.resistance()) {
            player.addPotionEffect(effect(PotionEffectType.RESISTANCE, 1));
        }
    }

    record EffectProfile(int speedAmplifier, boolean resistance) {
    }
}
