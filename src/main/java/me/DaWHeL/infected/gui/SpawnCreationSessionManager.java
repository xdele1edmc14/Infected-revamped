package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.PluginTaskScheduler;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnCreationSessionManager {
    private static final long TIMEOUT_TICKS = 60L * 20L;

    private final AdminSetupService setupService;
    private final PluginTaskScheduler scheduler;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile AdminGuiNavigator navigator;

    public SpawnCreationSessionManager(AdminSetupService setupService, PluginTaskScheduler scheduler) {
        this.setupService = Objects.requireNonNull(setupService, "setupService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void bindNavigator(AdminGuiNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    public void begin(Player player, SpawnRole role, int page) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(role, "role");
        cancel(player, false);
        UUID playerId = player.getUniqueId();
        Session pending = new Session(player, role, Math.max(0, page));
        sessions.put(playerId, pending);
        pending.timeoutTask = scheduler.runLater(() -> timeout(playerId, pending), TIMEOUT_TICKS);
        player.closeInventory();
        player.sendMessage(ChatColor.AQUA + "Walk to the desired location, then type a unique spawn name"
                + " in chat within 60 seconds.");
        player.sendMessage(ChatColor.GRAY + "Allowed: letters, numbers, _ and -. Type "
                + ChatColor.YELLOW + "close" + ChatColor.GRAY + " to cancel and return.");
    }

    public boolean hasSession(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    public boolean capture(Player player, String message) {
        if (player == null) {
            return false;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        String name = message == null ? "" : message.trim();
        if (name.equalsIgnoreCase("close")) {
            cancel(player, true);
            player.sendMessage(ChatColor.YELLOW + "Spawn creation cancelled.");
            return true;
        }
        try {
            AdminSetupService.validatePointName(name);
            boolean duplicate = setupService.teleportPoints(session.role).stream()
                    .anyMatch(point -> point.name().equalsIgnoreCase(name));
            if (duplicate) {
                throw new IllegalArgumentException(
                        "A " + session.role.displayName().toLowerCase(java.util.Locale.ROOT)
                                + " spawn named '" + name + "' already exists.");
            }
            setupService.saveTeleportPoint(session.role, name, player.getLocation());
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            player.sendMessage(ChatColor.GRAY + "Try another name, or type " + ChatColor.YELLOW
                    + "close" + ChatColor.GRAY + " to cancel.");
            return false;
        }

        sessions.remove(player.getUniqueId(), session);
        cancelTask(session);
        player.sendMessage(ChatColor.GREEN + "Saved "
                + session.role.displayName().toLowerCase(java.util.Locale.ROOT) + " spawn '"
                + name + "'. No arena blocks were changed.");
        reopen(session);
        return true;
    }

    public boolean cancel(Player player, boolean reopen) {
        if (player == null) {
            return false;
        }
        Session removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return false;
        }
        cancelTask(removed);
        if (reopen && player.isOnline()) {
            reopen(removed);
        }
        return true;
    }

    public void cancelAll() {
        for (Session session : sessions.values()) {
            cancelTask(session);
        }
        sessions.clear();
    }

    private void timeout(UUID playerId, Session expected) {
        if (!sessions.remove(playerId, expected)) {
            return;
        }
        expected.timeoutTask = null;
        if (expected.player.isOnline()) {
            expected.player.sendMessage(ChatColor.YELLOW
                    + "Spawn creation timed out. Open the spawn list and click Add Current Location to try again.");
        }
    }

    private void reopen(Session session) {
        AdminGuiNavigator currentNavigator = navigator;
        if (currentNavigator == null) {
            throw new IllegalStateException("Spawn creation navigator has not been bound.");
        }
        currentNavigator.openTeleportPoints(session.player, session.role, session.page);
    }

    private static void cancelTask(Session session) {
        BukkitTask task = session.timeoutTask;
        session.timeoutTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    private static final class Session {
        private final Player player;
        private final SpawnRole role;
        private final int page;
        private BukkitTask timeoutTask;

        private Session(Player player, SpawnRole role, int page) {
            this.player = player;
            this.role = role;
            this.page = page;
        }
    }
}
