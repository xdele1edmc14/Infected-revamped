package me.DaWHeL.infected;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

final class ScoreboardUpdateScheduler {
    private final Plugin plugin;
    private final BukkitScheduler scheduler;
    private final Runnable update;
    private BukkitTask task;

    ScoreboardUpdateScheduler(Plugin plugin, BukkitScheduler scheduler, Runnable update) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.update = Objects.requireNonNull(update, "update");
    }

    void reschedule(long intervalTicks) {
        if (task != null) {
            task.cancel();
        }
        task = scheduler.runTaskTimer(plugin, update, 0L, Math.max(1L, intervalTicks));
    }

    void shutdown() {
        if (task == null) return;
        task.cancel();
        task = null;
    }
}
