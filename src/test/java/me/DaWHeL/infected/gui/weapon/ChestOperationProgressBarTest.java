package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.loot.ChestOperationProgress;
import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChestOperationProgressBarTest {
    @Test
    void displaysRealProgressThenRemovesItAfterSuccess() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BossBar bossBar = mock(BossBar.class);
        Player player = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.createBossBar(anyString(), any(), any(), any(BarFlag[].class))).thenReturn(bossBar);
        ChestOperationProgressBar progressBar = new ChestOperationProgressBar(plugin, player);

        progressBar.update(new ChestOperationProgress("Filling chests", 25, 100));

        verify(bossBar).addPlayer(player);
        verify(bossBar).setTitle("Filling chests — 25% (25 / 100)");
        verify(bossBar).setProgress(0.25D);

        progressBar.complete(true, 100);

        verify(bossBar).setColor(BarColor.GREEN);
        verify(bossBar).setTitle("Chest operation complete — 100 chests");
        ArgumentCaptor<Runnable> cleanup = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), cleanup.capture(), eq(40L));
        cleanup.getValue().run();
        verify(bossBar).removeAll();
    }

    @Test
    void closeImmediatelyRemovesTheViewerAndIsIdempotent() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        BossBar bossBar = mock(BossBar.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.createBossBar(anyString(), any(), eq(BarStyle.SEGMENTED_10), any(BarFlag[].class)))
                .thenReturn(bossBar);
        ChestOperationProgressBar progressBar = new ChestOperationProgressBar(plugin, mock(Player.class));

        progressBar.close();
        progressBar.close();

        verify(bossBar).removeAll();
    }
}
