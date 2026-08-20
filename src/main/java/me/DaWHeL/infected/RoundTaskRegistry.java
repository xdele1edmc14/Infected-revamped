package me.DaWHeL.infected;

import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RoundTaskRegistry {
    private final Set<BukkitTask> gameplayTasks = new LinkedHashSet<>();
    private final Set<BukkitTask> cleanupTasks = new LinkedHashSet<>();
    private boolean gameplayOpen = true;
    private boolean cleanupOpen = true;

    public BukkitTask trackGameplay(BukkitTask task) {
        return track(task, gameplayTasks, gameplayOpen);
    }

    public BukkitTask trackCleanup(BukkitTask task) {
        return track(task, cleanupTasks, cleanupOpen);
    }

    public void forget(BukkitTask task) {
        if (task == null) {
            return;
        }
        gameplayTasks.remove(task);
        cleanupTasks.remove(task);
    }

    public void cancelGameplay() {
        cancel(gameplayTasks);
        gameplayOpen = false;
    }

    public void cancelAll() {
        cancel(gameplayTasks);
        cancel(cleanupTasks);
        gameplayOpen = false;
        cleanupOpen = false;
    }

    public void resetForNewRound() {
        cancelAll();
        gameplayOpen = true;
        cleanupOpen = true;
    }

    int gameplayTaskCount() {
        return gameplayTasks.size();
    }

    int cleanupTaskCount() {
        return cleanupTasks.size();
    }

    private static BukkitTask track(BukkitTask task, Set<BukkitTask> tasks, boolean open) {
        if (task == null) {
            return null;
        }
        if (!open) {
            task.cancel();
            return task;
        }
        tasks.add(task);
        return task;
    }

    private static void cancel(Set<BukkitTask> tasks) {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}

