package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ScoreboardManager {
    private static final int MAX_SIDEBAR_LINES = 15;

    private final GameManager gameManager;
    private final InfectedPlugin plugin;
    private final ScoreboardTextRenderer textRenderer = new ScoreboardTextRenderer();
    private final Map<UUID, PersonalBoard> boards = new HashMap<>();
    private final Set<UUID> playersOnMainBoard = new HashSet<>();
    private CachedConfiguration cachedConfiguration;

    public ScoreboardManager(InfectedPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void updateScoreboard() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            applyScoreboard(player);
        }
        boards.keySet().retainAll(online);
        playersOnMainBoard.retainAll(online);
    }

    public void applyScoreboard(Player player) {
        CachedConfiguration config = configuration();
        if (!config.enabled()) {
            clearScoreboard(player);
            return;
        }

        playersOnMainBoard.remove(player.getUniqueId());
        ParticipantRole role = gameManager.roleOf(player);
        ScoreboardTemplate.State state = new ScoreboardTemplate.State(
                role,
                gameManager.kills(player),
                gameManager.infections(player),
                gameManager.remainingInfectedLives(player),
                gameManager.configuredInfectedLives(),
                gameManager.getSurvivors().size(),
                gameManager.getInfected().size(),
                gameManager.roundModeDisplayName(),
                gameManager.roundTimeDisplay()
        );
        ViewState viewState = new ViewState(
                config.title(),
                config.template().lines(role),
                config.template().placeholders(state)
        );

        PersonalBoard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = createBoard();
            boards.put(player.getUniqueId(), board);
            player.setScoreboard(board.scoreboard);
        }
        board.update(viewState);
    }

    public void clearScoreboard(Player player) {
        boards.remove(player.getUniqueId());
        if (playersOnMainBoard.add(player.getUniqueId())) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearCachedBoards() {
        boards.clear();
        playersOnMainBoard.clear();
    }

    public void reloadConfiguration() {
        FileConfiguration config = Objects.requireNonNull(plugin.getConfig(), "plugin config");
        cachedConfiguration = new CachedConfiguration(
                config.getBoolean("scoreboard.enabled", true),
                Objects.requireNonNullElse(config.getString("scoreboard.title", ""), ""),
                new ScoreboardTemplate(config));
    }

    public void forgetPlayer(Player player) {
        boards.remove(player.getUniqueId());
        playersOnMainBoard.remove(player.getUniqueId());
    }

    private PersonalBoard createBoard() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "infectedStats", Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team[] lines = new Team[MAX_SIDEBAR_LINES];
        String[] entries = new String[MAX_SIDEBAR_LINES];
        for (int index = 0; index < MAX_SIDEBAR_LINES; index++) {
            String entry = ChatColor.values()[index].toString() + ChatColor.RESET;
            Team line = scoreboard.registerNewTeam("infected-line-" + index);
            line.addEntry(entry);
            lines[index] = line;
            entries[index] = entry;
        }
        return new PersonalBoard(scoreboard, objective, lines, entries);
    }

    private CachedConfiguration configuration() {
        if (cachedConfiguration == null) {
            reloadConfiguration();
        }
        return cachedConfiguration;
    }

    private final class PersonalBoard {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team[] lines;
        private final String[] entries;
        private ViewState lastState;
        private int visibleLines;

        private PersonalBoard(Scoreboard scoreboard, Objective objective, Team[] lines, String[] entries) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.lines = lines;
            this.entries = entries;
        }

        private void update(ViewState state) {
            if (state.equals(lastState)) {
                return;
            }

            objective.displayName(textRenderer.render(state.title, state.placeholders));
            int nextVisibleLines = Math.min(state.lines.size(), MAX_SIDEBAR_LINES);
            for (int index = 0; index < nextVisibleLines; index++) {
                lines[index].prefix(textRenderer.render(state.lines.get(index), state.placeholders));
                objective.getScore(entries[index]).setScore(nextVisibleLines - index);
            }
            for (int index = nextVisibleLines; index < visibleLines; index++) {
                scoreboard.resetScores(entries[index]);
                lines[index].prefix(Component.empty());
            }
            visibleLines = nextVisibleLines;
            lastState = state;
        }
    }

    private record ViewState(String title, List<String> lines, Map<String, String> placeholders) {
    }

    private record CachedConfiguration(boolean enabled, String title, ScoreboardTemplate template) {
    }
}
