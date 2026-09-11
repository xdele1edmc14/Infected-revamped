package me.DaWHeL.infected;

import org.bukkit.Server;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundTimeBossBarTest {

    @Test
    void rendersRemainingTimeAndReconcilesOnlineViewers() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        BossBar bossBar = mock(BossBar.class);
        Player retained = mock(Player.class);
        Player removed = mock(Player.class);
        Player added = mock(Player.class);
        List<Player> current = new ArrayList<>(List.of(retained, removed));
        when(retained.isOnline()).thenReturn(true);
        when(added.isOnline()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.createBossBar(anyString(), eq(BarColor.YELLOW), eq(BarStyle.SOLID),
                any(BarFlag[].class))).thenReturn(bossBar);
        when(bossBar.getPlayers()).thenReturn(current);
        RoundTimeBossBar bar = new RoundTimeBossBar(
                plugin, "&eTime Remaining: &f{time}", BarColor.YELLOW, BarStyle.SOLID);

        bar.update(90, 180, List.of(retained, added));

        verify(bossBar).setTitle("§eTime Remaining: §f1:30");
        verify(bossBar).setProgress(0.5D);
        verify(bossBar).removePlayer(removed);
        verify(bossBar).addPlayer(added);
        verify(bossBar, never()).addPlayer(retained);
    }

    @Test
    void clampsProgressAndCloseIsIdempotent() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        BossBar bossBar = mock(BossBar.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.createBossBar(anyString(), any(), any(), any(BarFlag[].class))).thenReturn(bossBar);
        when(bossBar.getPlayers()).thenReturn(List.of());
        RoundTimeBossBar bar = new RoundTimeBossBar(
                plugin, "{time}", BarColor.RED, BarStyle.SEGMENTED_10);

        bar.update(-5, 0, List.of());
        bar.close();
        bar.close();

        verify(bossBar).setProgress(0.0D);
        verify(bossBar).setTitle("0:00");
        verify(bossBar).removeAll();
    }
}
