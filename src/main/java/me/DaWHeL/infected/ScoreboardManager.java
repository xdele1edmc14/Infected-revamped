package me.DaWHeL.infected;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;

public class ScoreboardManager {
    private static final int MAX_SIDEBAR_LINES = 15;

    private final GameManager gameManager;
    private final InfectedPlugin plugin;
    private final ScoreboardTextRenderer textRenderer = new ScoreboardTextRenderer();

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
        if (!config.getBoolean("scoreboard.enabled", true)) {
            clearScoreboard(player);
            return;
        }

        ParticipantRole role = gameManager.roleOf(player);
        ScoreboardTemplate template = new ScoreboardTemplate(config);
        ScoreboardTemplate.State state = new ScoreboardTemplate.State(
                role,
                gameManager.kills(player),
                gameManager.infections(player),
                gameManager.remainingInfectedLives(player),
                gameManager.configuredInfectedLives(),
                gameManager.getSurvivors().size(),
                gameManager.getInfected().size()
        );
        Map<String, String> placeholders = template.placeholders(state);
        List<String> lines = template.lines(role);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "infectedStats",
                Criteria.DUMMY,
                textRenderer.render(config.getString("scoreboard.title", ""), placeholders)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int visibleLines = Math.min(lines.size(), MAX_SIDEBAR_LINES);
        for (int index = 0; index < visibleLines; index++) {
            String entry = ChatColor.values()[index].toString() + ChatColor.RESET;
            Team line = scoreboard.registerNewTeam("infected-line-" + index);
            line.addEntry(entry);
            line.prefix(textRenderer.render(lines.get(index), placeholders));
            objective.getScore(entry).setScore(visibleLines - index);
        }

        player.setScoreboard(scoreboard);
    }

    public void clearScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
