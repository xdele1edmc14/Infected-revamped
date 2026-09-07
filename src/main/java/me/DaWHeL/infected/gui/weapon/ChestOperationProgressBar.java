package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.loot.ChestOperationProgress;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Objects;

final class ChestOperationProgressBar {
    private final InfectedPlugin plugin;
    private final BossBar bossBar;
    private boolean closed;

    ChestOperationProgressBar(InfectedPlugin plugin, Player player) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(player, "player");
        bossBar = plugin.getServer().createBossBar(
                "Starting chest operation...", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.setProgress(0.0D);
        bossBar.addPlayer(player);
    }

    void update(ChestOperationProgress progress) {
        if (closed) return;
        long percentage = Math.round(progress.fraction() * 100.0D);
        bossBar.setTitle(progress.stage() + " — " + percentage + "% ("
                + progress.completed() + " / " + progress.total() + ")");
        bossBar.setProgress(progress.fraction());
    }

    void complete(boolean success, int affectedChests) {
        if (closed) return;
        bossBar.setColor(success ? BarColor.GREEN : BarColor.RED);
        bossBar.setProgress(1.0D);
        bossBar.setTitle(success
                ? "Chest operation complete — " + affectedChests + " chests"
                : "Chest operation failed");
        plugin.getServer().getScheduler().runTaskLater(plugin, this::close, 40L);
    }

    void close() {
        if (closed) return;
        closed = true;
        bossBar.removeAll();
    }
}
