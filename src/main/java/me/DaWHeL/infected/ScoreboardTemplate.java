package me.DaWHeL.infected;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScoreboardTemplate {
    private final ConfigurationSection config;

    public ScoreboardTemplate(ConfigurationSection config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<String> lines(ParticipantRole role) {
        List<String> roleLines = config.getStringList("scoreboard.layouts." + roleKey(role));
        return roleLines.isEmpty() ? config.getStringList("scoreboard.lines") : roleLines;
    }

    public Map<String, String> placeholders(State state) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("role", config.getString(
                "scoreboard.role-names." + roleKey(state.role()), state.role().name()));
        placeholders.put("kills", Integer.toString(state.kills()));
        placeholders.put("infections", Integer.toString(state.infections()));
        placeholders.put("lives", Integer.toString(state.remainingLives()));
        placeholders.put("max_lives", Integer.toString(state.maxLives()));
        placeholders.put("lives_hearts", lives(state.remainingLives(), state.maxLives()));
        placeholders.put("survivors", Integer.toString(state.survivors()));
        placeholders.put("infected", Integer.toString(state.infected()));
        placeholders.put("outbreak_bar", ScoreboardBar.build(
                state.survivors(),
                state.infected(),
                config.getInt("scoreboard.outbreak.segments", 12),
                config.getString("scoreboard.outbreak.survivor-segment", "<blue>■"),
                config.getString("scoreboard.outbreak.infected-segment", "<red>■"),
                config.getString("scoreboard.outbreak.empty-segment", "<dark_gray>■")
        ));
        return Map.copyOf(placeholders);
    }

    private String lives(int remaining, int maximum) {
        int safeMaximum = Math.min(InfectedLifeTracker.MAX_LIVES, Math.max(0, maximum));
        int safeRemaining = Math.max(0, Math.min(remaining, safeMaximum));
        String full = config.getString("scoreboard.lives.full", "<red>♥");
        String empty = config.getString("scoreboard.lives.empty", "<dark_gray>♡");
        return full.repeat(safeRemaining) + empty.repeat(safeMaximum - safeRemaining);
    }

    private static String roleKey(ParticipantRole role) {
        return switch (role) {
            case SURVIVOR -> "survivor";
            case INFECTED -> "infected";
            case NONE -> "spectator";
        };
    }

    public record State(
            ParticipantRole role,
            int kills,
            int infections,
            int remainingLives,
            int maxLives,
            int survivors,
            int infected
    ) {
        public State {
            Objects.requireNonNull(role, "role");
        }
    }
}
