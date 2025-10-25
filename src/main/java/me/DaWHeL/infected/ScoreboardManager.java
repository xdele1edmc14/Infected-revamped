package me.DaWHeL.infected;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class ScoreboardManager {

    private final GameManager gameManager;
    private final InfectedPlugin plugin;

    public ScoreboardManager(InfectedPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void updateScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyScoreboard(player);
        }
    }

    public void applyScoreboard(Player player) {
        FileConfiguration config = plugin.getConfig();
        String title = ChatColor.translateAlternateColorCodes('&',
                config.getString("scoreboard.title", "&a&lInfected Game"));

        List<String> lines = config.getStringList("scoreboard.lines");

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("infectedStats", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int infectedCount = gameManager.getInfected().size();
        int survivorCount = gameManager.getSurvivors().size();

        int score = lines.size();
        for (String line : lines) {
            String formatted = line
                    .replace("{infected}", String.valueOf(infectedCount))
                    .replace("{survivors}", String.valueOf(survivorCount));
            objective.getScore(ChatColor.translateAlternateColorCodes('&', formatted)).setScore(score);
            score--;
        }

        player.setScoreboard(scoreboard);
    }

    public void clearScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}

