package me.DaWHeL.infected;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class RoundTimeBossBar {
    private final BossBar bossBar;
    private final String titleTemplate;
    private boolean closed;

    RoundTimeBossBar(
            InfectedPlugin plugin,
            String titleTemplate,
            BarColor color,
            BarStyle style
    ) {
        Objects.requireNonNull(plugin, "plugin");
        this.titleTemplate = Objects.requireNonNullElse(titleTemplate, "&eTime Remaining: &f{time}");
        bossBar = Objects.requireNonNull(plugin.getServer().createBossBar(
                "Time Remaining", Objects.requireNonNull(color, "color"),
                Objects.requireNonNull(style, "style")), "bossBar");
    }

    void update(int remainingSeconds, int totalSeconds, Iterable<? extends Player> viewers) {
        if (closed) return;
        int safeRemaining = Math.max(0, remainingSeconds);
        int safeTotal = Math.max(1, totalSeconds);
        double progress = Math.max(0.0D, Math.min(1.0D, (double) safeRemaining / safeTotal));
        String time = (safeRemaining / 60) + ":"
                + String.format(java.util.Locale.ROOT, "%02d", safeRemaining % 60);
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('&',
                titleTemplate.replace("{time}", time)));
        bossBar.setProgress(progress);

        Set<Player> expected = new LinkedHashSet<>();
        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                expected.add(viewer);
            }
        }
        List<Player> current = List.copyOf(bossBar.getPlayers());
        for (Player viewer : current) {
            if (!expected.contains(viewer)) {
                bossBar.removePlayer(viewer);
            }
        }
        for (Player viewer : expected) {
            if (!current.contains(viewer)) {
                bossBar.addPlayer(viewer);
            }
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        bossBar.removeAll();
    }
}
