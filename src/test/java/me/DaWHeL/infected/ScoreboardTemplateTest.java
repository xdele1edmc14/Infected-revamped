package me.DaWHeL.infected;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardTemplateTest {
    private MemoryConfiguration config;
    private ScoreboardTemplate template;

    @BeforeEach
    void setUp() {
        config = new MemoryConfiguration();
        config.set("scoreboard.layouts.survivor", List.of("survivor line", "{kills}"));
        config.set("scoreboard.layouts.infected", List.of("infected line", "{infections}", "{lives_hearts}"));
        config.set("scoreboard.layouts.spectator", List.of("spectator line"));
        config.set("scoreboard.role-names.survivor", "The Survivor");
        config.set("scoreboard.role-names.infected", "The Infected");
        config.set("scoreboard.role-names.spectator", "Watching");
        config.set("scoreboard.lives.full", "H");
        config.set("scoreboard.lives.empty", "h");
        config.set("scoreboard.outbreak.segments", 6);
        config.set("scoreboard.outbreak.survivor-segment", "B");
        config.set("scoreboard.outbreak.infected-segment", "R");
        config.set("scoreboard.outbreak.empty-segment", "E");
        template = new ScoreboardTemplate(config);
    }

    @Test
    void selectsTheLayoutForThePlayersCurrentRole() {
        assertEquals(List.of("survivor line", "{kills}"), template.lines(ParticipantRole.SURVIVOR));
        assertEquals(List.of("infected line", "{infections}", "{lives_hearts}"),
                template.lines(ParticipantRole.INFECTED));
        assertEquals(List.of("spectator line"), template.lines(ParticipantRole.NONE));
    }

    @Test
    void suppliesPersonalTeamLifeAndOutbreakPlaceholders() {
        ScoreboardTemplate.State state = new ScoreboardTemplate.State(
                ParticipantRole.INFECTED, 4, 3, 2, 3, 12, 6,
                "Time Limit", "7:05");

        Map<String, String> placeholders = template.placeholders(state);

        assertEquals("The Infected", placeholders.get("role"));
        assertEquals("4", placeholders.get("kills"));
        assertEquals("3", placeholders.get("infections"));
        assertEquals("2", placeholders.get("lives"));
        assertEquals("3", placeholders.get("max_lives"));
        assertEquals("HHh", placeholders.get("lives_hearts"));
        assertEquals("12", placeholders.get("survivors"));
        assertEquals("6", placeholders.get("infected"));
        assertEquals("BBBBRR", placeholders.get("outbreak_bar"));
        assertEquals("Time Limit", placeholders.get("round_mode"));
        assertEquals("7:05", placeholders.get("time_remaining"));
    }

    @Test
    void fallsBackToTheExistingSharedLinesForOldConfigs() {
        MemoryConfiguration legacyConfig = new MemoryConfiguration();
        legacyConfig.set("scoreboard.lines", List.of("{survivors}", "{infected}"));

        assertEquals(List.of("{survivors}", "{infected}"),
                new ScoreboardTemplate(legacyConfig).lines(ParticipantRole.INFECTED));
    }

    @Test
    void capsLifeHeartRenderingBeforeRepeatingConfiguredGlyphs() {
        ScoreboardTemplate.State state = new ScoreboardTemplate.State(
                ParticipantRole.INFECTED, 0, 0, 1_000_000, 1_000_000, 1, 1,
                "Deathmatch", "No Limit");

        assertEquals(64, template.placeholders(state).get("lives_hearts").length());
    }

    @Test
    void parsesAnImmutableSnapshotInsteadOfReadingYamlForEveryPlayer() {
        config.set("scoreboard.layouts.survivor", List.of("changed"));
        config.set("scoreboard.role-names.survivor", "Changed Role");
        ScoreboardTemplate.State state = new ScoreboardTemplate.State(
                ParticipantRole.SURVIVOR, 0, 0, 0, 3, 1, 0,
                "Deathmatch", "No Limit");

        assertEquals(List.of("survivor line", "{kills}"), template.lines(ParticipantRole.SURVIVOR));
        assertEquals("The Survivor", template.placeholders(state).get("role"));
    }
}
