package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerStateSnapshot {
    private static final Logger LOGGER = Logger.getLogger(PlayerStateSnapshot.class.getName());
    private final Location location;
    private final UUID locationWorldId;
    private final GameMode gameMode;
    private final boolean glowing;
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final int heldItemSlot;
    private final List<PotionEffect> potionEffects;
    private final Component playerListName;
    private final Component playerListHeader;
    private final Component playerListFooter;
    private final Scoreboard scoreboard;
    private final Location compassTarget;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;

    private PlayerStateSnapshot(
            Location location,
            UUID locationWorldId,
            GameMode gameMode,
            boolean glowing,
            ItemStack[] storageContents,
            ItemStack[] armorContents,
            ItemStack offHand,
            int heldItemSlot,
            List<PotionEffect> potionEffects,
            Component playerListName,
            Component playerListHeader,
            Component playerListFooter,
            Scoreboard scoreboard,
            Location compassTarget,
            int foodLevel,
            float saturation,
            float exhaustion
    ) {
        this.location = copy(location);
        this.locationWorldId = locationWorldId;
        this.gameMode = gameMode;
        this.glowing = glowing;
        this.storageContents = copy(storageContents);
        this.armorContents = copy(armorContents);
        this.offHand = copy(offHand);
        this.heldItemSlot = heldItemSlot;
        this.potionEffects = List.copyOf(potionEffects);
        this.playerListName = playerListName;
        this.playerListHeader = playerListHeader;
        this.playerListFooter = playerListFooter;
        this.scoreboard = scoreboard;
        this.compassTarget = copy(compassTarget);
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
    }

    public static PlayerStateSnapshot capture(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        Location location = player.getLocation();
        World world = location.getWorld();
        return new PlayerStateSnapshot(
                location,
                world == null ? null : world.getUID(),
                player.getGameMode(),
                player.isGlowing(),
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getItemInOffHand(),
                inventory.getHeldItemSlot(),
                List.copyOf(player.getActivePotionEffects()),
                player.playerListName(),
                player.playerListHeader(),
                player.playerListFooter(),
                player.getScoreboard(),
                player.getCompassTarget(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion()
        );
    }

    public boolean restore(Player player) {
        Objects.requireNonNull(player, "player");
        boolean stateRestored = restoreState(player);
        boolean locationRestored = attemptResult(
                player, "location", () -> player.teleport(restorationLocation(player)));
        return stateRestored && locationRestored;
    }

    RespawnRestoration restoreForRespawn(Player player) {
        Objects.requireNonNull(player, "player");
        boolean stateRestored = restoreState(player);
        try {
            Location destination = restorationLocation(player);
            return new RespawnRestoration(destination, stateRestored && destination != null);
        } catch (RuntimeException exception) {
            logFailure(player, "location", exception);
            return new RespawnRestoration(null, false);
        }
    }

    private boolean restoreState(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean restored = true;

        restored &= attempt(player, "inventory storage",
                () -> inventory.setStorageContents(copy(storageContents)));
        restored &= attempt(player, "armor", () -> inventory.setArmorContents(copy(armorContents)));
        restored &= attempt(player, "off-hand item", () -> inventory.setItemInOffHand(copy(offHand)));
        restored &= attempt(player, "held slot", () -> inventory.setHeldItemSlot(heldItemSlot));
        restored &= attempt(player, "potion effects", () -> restoreEffects(player));
        restored &= attempt(player, "game mode", () -> player.setGameMode(gameMode));
        restored &= attempt(player, "glowing state", () -> player.setGlowing(glowing));
        restored &= attempt(player, "tab-list name", () -> player.playerListName(playerListName));
        restored &= attempt(player, "tab-list header and footer",
                () -> player.sendPlayerListHeaderAndFooter(playerListHeader, playerListFooter));
        restored &= attempt(player, "scoreboard", () -> player.setScoreboard(scoreboard));
        restored &= attempt(player, "compass target", () -> player.setCompassTarget(copy(compassTarget)));
        restored &= attempt(player, "food level", () -> player.setFoodLevel(foodLevel));
        restored &= attempt(player, "saturation", () -> player.setSaturation(saturation));
        restored &= attempt(player, "exhaustion", () -> player.setExhaustion(exhaustion));
        return restored;
    }

    private void restoreEffects(Player player) {
        Collection<PotionEffect> currentEffects = List.copyOf(player.getActivePotionEffects());
        for (PotionEffect effect : currentEffects) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }
    }

    private static boolean attempt(Player player, String property, Runnable restoration) {
        try {
            restoration.run();
            return true;
        } catch (RuntimeException exception) {
            logFailure(player, property, exception);
            return false;
        }
    }

    private static boolean attemptResult(
            Player player,
            String property,
            java.util.function.BooleanSupplier restoration
    ) {
        try {
            boolean restored = restoration.getAsBoolean();
            if (!restored) {
                LOGGER.warning("Could not restore " + property + " for player "
                        + player.getUniqueId() + ": operation was cancelled.");
            }
            return restored;
        } catch (RuntimeException exception) {
            logFailure(player, property, exception);
            return false;
        }
    }

    private static void logFailure(Player player, String property, RuntimeException exception) {
        LOGGER.log(Level.WARNING,
                "Could not restore " + property + " for player " + player.getUniqueId(),
                exception);
    }

    private Location restorationLocation(Player player) {
        if (locationWorldId != null) {
            Server server = player.getServer();
            World loadedWorld = server == null ? null : server.getWorld(locationWorldId);
            if (loadedWorld != null) {
                Location restored = copy(location);
                restored.setWorld(loadedWorld);
                return restored;
            }
        }
        return player.getWorld().getSpawnLocation();
    }

    private static ItemStack[] copy(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = copy(source[index]);
        }
        return result;
    }

    private static ItemStack copy(ItemStack source) {
        return source == null ? null : source.clone();
    }

    private static Location copy(Location source) {
        return source == null ? null : source.clone();
    }

    record RespawnRestoration(Location location, boolean success) {
    }
}
