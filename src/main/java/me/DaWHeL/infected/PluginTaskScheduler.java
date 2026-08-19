package me.DaWHeL.infected;

import org.bukkit.scheduler.BukkitTask;

public interface PluginTaskScheduler {
    BukkitTask runRepeating(Runnable operation, long delayTicks, long periodTicks);

    BukkitTask runLater(Runnable operation, long delayTicks);
}
