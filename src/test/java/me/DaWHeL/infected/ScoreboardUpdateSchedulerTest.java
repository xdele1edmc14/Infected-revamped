package me.DaWHeL.infected;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreboardUpdateSchedulerTest {
    @Test
    void rescheduleCancelsTheOldTaskAndUsesTheNewInterval() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Runnable update = mock(Runnable.class);
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(plugin, update, 0L, 20L)).thenReturn(first);
        when(scheduler.runTaskTimer(plugin, update, 0L, 5L)).thenReturn(second);
        ScoreboardUpdateScheduler owner = new ScoreboardUpdateScheduler(plugin, scheduler, update);

        owner.reschedule(20L);
        owner.reschedule(5L);

        var order = inOrder(first, scheduler);
        order.verify(first).cancel();
        order.verify(scheduler).runTaskTimer(plugin, update, 0L, 5L);
        owner.shutdown();
        verify(second).cancel();
    }

    @Test
    void clampsInvalidIntervalsToOneTick() {
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Runnable update = mock(Runnable.class);
        when(scheduler.runTaskTimer(plugin, update, 0L, 1L)).thenReturn(mock(BukkitTask.class));

        new ScoreboardUpdateScheduler(plugin, scheduler, update).reschedule(0L);

        verify(scheduler).runTaskTimer(plugin, update, 0L, 1L);
    }
}
