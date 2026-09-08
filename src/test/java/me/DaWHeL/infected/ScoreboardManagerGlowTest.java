package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreboardManagerGlowTest {

    @Test
    void reusesTwoHundredFiftyPersonalBoardsAndLineTeamsAcrossUnchangedRefreshes() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        List<Player> viewers = IntStream.range(0, 250)
                .mapToObj(index -> player("viewer-" + index))
                .toList();
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Team lineTeam = mock(Team.class);
        Score score = mock(Score.class);
        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboards =
                mock(org.bukkit.scoreboard.ScoreboardManager.class);

        YamlConfiguration config = new YamlConfiguration();
        config.set("scoreboard.enabled", true);
        config.set("scoreboard.title", "<red>Infected");
        config.set("scoreboard.lines", List.of("Players: {survivors}"));
        when(plugin.getConfig()).thenReturn(config);
        when(gameManager.roleOf(any(Player.class))).thenReturn(ParticipantRole.NONE);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        when(bukkitScoreboards.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective(anyString(),
                nullable(org.bukkit.scoreboard.Criteria.class), any(Component.class)))
                .thenReturn(objective);
        when(scoreboard.registerNewTeam(anyString())).thenReturn(lineTeam);
        when(objective.getScore(anyString())).thenReturn(score);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(bukkitScoreboards);
            ScoreboardManager manager = new ScoreboardManager(plugin, gameManager);

            viewers.forEach(manager::applyScoreboard);
            viewers.forEach(manager::applyScoreboard);
        }

        verify(bukkitScoreboards, times(250)).getNewScoreboard();
        verify(scoreboard, times(250)).registerNewObjective(anyString(),
                nullable(org.bukkit.scoreboard.Criteria.class), any(Component.class));
        verify(scoreboard, times(3_750)).registerNewTeam(anyString());
        verify(scoreboard, never()).registerNewTeam("survivor-glow");
        verify(lineTeam, times(250)).prefix(any(Component.class));
        viewers.forEach(viewer -> verify(viewer, times(1)).setScoreboard(scoreboard));
    }

    @Test
    void disabledRefreshUsesMainBoardWithoutAllocatingBlankBoards() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player viewer = player("disabled-viewer");
        Scoreboard mainScoreboard = mock(Scoreboard.class);
        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboards =
                mock(org.bukkit.scoreboard.ScoreboardManager.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("scoreboard.enabled", false);
        when(plugin.getConfig()).thenReturn(config);
        when(bukkitScoreboards.getMainScoreboard()).thenReturn(mainScoreboard);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(bukkitScoreboards);
            ScoreboardManager manager = new ScoreboardManager(plugin, gameManager);

            manager.applyScoreboard(viewer);
            manager.applyScoreboard(viewer);
        }

        verify(bukkitScoreboards, never()).getNewScoreboard();
        verify(viewer, times(1)).setScoreboard(mainScoreboard);
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }
}
