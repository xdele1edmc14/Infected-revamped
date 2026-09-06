package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Survivor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreboardManagerGlowTest {

    @Test
    void colorsSurvivorGlowCyanOnEveryViewerScoreboard() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player viewer = mock(Player.class);
        Player target = mock(Player.class);
        Survivor survivor = mock(Survivor.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        Team survivorGlow = mock(Team.class);
        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboards =
                mock(org.bukkit.scoreboard.ScoreboardManager.class);

        YamlConfiguration config = new YamlConfiguration();
        config.set("scoreboard.enabled", true);
        when(plugin.getConfig()).thenReturn(config);
        when(gameManager.roleOf(viewer)).thenReturn(ParticipantRole.NONE);
        when(gameManager.getSurvivors()).thenReturn(List.of(survivor));
        when(gameManager.getInfected()).thenReturn(List.of());
        when(survivor.getPlayer()).thenReturn(target);
        when(target.getName()).thenReturn("Target");
        when(bukkitScoreboards.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewObjective(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(org.bukkit.scoreboard.Criteria.class),
                org.mockito.ArgumentMatchers.any(Component.class)))
                .thenReturn(objective);
        when(scoreboard.registerNewTeam("survivor-glow")).thenReturn(survivorGlow);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(bukkitScoreboards);

            new ScoreboardManager(plugin, gameManager).applyScoreboard(viewer);
        }

        verify(survivorGlow).setColor(ChatColor.AQUA);
        verify(survivorGlow).addEntry("Target");
        verify(viewer).setScoreboard(scoreboard);
    }

    @Test
    void keepsCyanSurvivorGlowWhenSidebarIsCleared() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        Player viewer = mock(Player.class);
        Player target = mock(Player.class);
        Survivor survivor = mock(Survivor.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Team survivorGlow = mock(Team.class);
        org.bukkit.scoreboard.ScoreboardManager bukkitScoreboards =
                mock(org.bukkit.scoreboard.ScoreboardManager.class);

        when(gameManager.getSurvivors()).thenReturn(List.of(survivor));
        when(survivor.getPlayer()).thenReturn(target);
        when(target.getName()).thenReturn("Target");
        when(bukkitScoreboards.getNewScoreboard()).thenReturn(scoreboard);
        when(scoreboard.registerNewTeam("survivor-glow")).thenReturn(survivorGlow);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScoreboardManager).thenReturn(bukkitScoreboards);

            new ScoreboardManager(plugin, gameManager).clearScoreboard(viewer);
        }

        verify(survivorGlow).setColor(ChatColor.AQUA);
        verify(survivorGlow).addEntry("Target");
        verify(viewer).setScoreboard(scoreboard);
    }
}
