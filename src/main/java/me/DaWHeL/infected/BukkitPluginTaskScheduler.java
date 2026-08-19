package me.DaWHeL.infected;

import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class BukkitPluginTaskScheduler implements PluginTaskScheduler {
    private final InfectedPlugin plugin;

    public BukkitPluginTaskScheduler(InfectedPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public BukkitTask runRepeating(Runnable operation, long delayTicks, long periodTicks) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, operation, delayTicks, periodTicks);
    }

    @Override
    public BukkitTask runLater(Runnable operation, long delayTicks) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, operation, delayTicks);
    }
}
