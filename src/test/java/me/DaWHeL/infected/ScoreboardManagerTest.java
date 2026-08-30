package me.DaWHeL.infected;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ScoreboardManagerTest {

    @Test
    void updatesOnlyCurrentParticipantsAndQueuedSpectatorsDuringLiveRoundPhases() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player participant = mock(Player.class);
        Player queued = mock(Player.class);
        Player unrelated = mock(Player.class);
        ScoreboardManager scoreboards = spy(new ScoreboardManager(plugin, gameManager));
        doNothing().when(scoreboards).applyScoreboard(any(Player.class));
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.isRoundParticipant(participant)).thenReturn(true);
        when(gameManager.isQueued(queued)).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(participant, queued, unrelated));

            scoreboards.updateScoreboard();
        }

        verify(scoreboards).applyScoreboard(participant);
        verify(scoreboards).applyScoreboard(queued);
        verify(scoreboards, never()).applyScoreboard(unrelated);
    }

    @Test
    void doesNotOverwriteRestoredScoreboardsDuringEndingOrLobby() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        ScoreboardManager scoreboards = spy(new ScoreboardManager(plugin, gameManager));
        doNothing().when(scoreboards).applyScoreboard(any(Player.class));
        when(gameManager.isRoundParticipant(player)).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(gameManager.getPhase()).thenReturn(RoundPhase.ENDING);
            scoreboards.updateScoreboard();
            when(gameManager.getPhase()).thenReturn(RoundPhase.LOBBY);
            scoreboards.updateScoreboard();
        }

        verify(scoreboards, never()).applyScoreboard(any(Player.class));
    }

    @Test
    void duplicateVisibleLinesReceiveDistinctInvisibleEntries() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("scoreboard.lines", List.of("&7Waiting", "&7Waiting"));
        when(plugin.getConfig()).thenReturn(config);
        org.bukkit.scoreboard.ScoreboardManager bukkitManager = mock(
                org.bukkit.scoreboard.ScoreboardManager.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Score score = mock(Score.class);
        when(bukkitManager.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective(any(String.class), any(String.class), any(String.class)))
                .thenReturn(objective);
        when(objective.getScore(any(String.class))).thenReturn(score);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(bukkitManager);
            new ScoreboardManager(plugin, gameManager).applyScoreboard(player);
        }

        org.mockito.ArgumentCaptor<String> entries = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(objective, org.mockito.Mockito.times(2)).getScore(entries.capture());
        assertNotEquals(entries.getAllValues().get(0), entries.getAllValues().get(1));
        assertEquals("Waiting", org.bukkit.ChatColor.stripColor(entries.getAllValues().get(0)));
        assertEquals("Waiting", org.bukkit.ChatColor.stripColor(entries.getAllValues().get(1)));
    }
}
