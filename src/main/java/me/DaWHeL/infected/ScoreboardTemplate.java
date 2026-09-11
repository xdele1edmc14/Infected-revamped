package me.DaWHeL.infected;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScoreboardTemplate {
    private final Map<ParticipantRole, List<String>> lines;
    private final Map<ParticipantRole, String> roleNames;
    private final String fullLife;
    private final String emptyLife;
    private final int outbreakSegments;
    private final String survivorSegment;
    private final String infectedSegment;
    private final String emptySegment;

    public ScoreboardTemplate(ConfigurationSection config) {
        Objects.requireNonNull(config, "config");
        List<String> sharedLines = List.copyOf(config.getStringList("scoreboard.lines"));
        EnumMap<ParticipantRole, List<String>> parsedLines = new EnumMap<>(ParticipantRole.class);
        EnumMap<ParticipantRole, String> parsedRoleNames = new EnumMap<>(ParticipantRole.class);
        for (ParticipantRole role : ParticipantRole.values()) {
            List<String> roleLines = List.copyOf(config.getStringList("scoreboard.layouts." + roleKey(role)));
            parsedLines.put(role, roleLines.isEmpty() ? sharedLines : roleLines);
            parsedRoleNames.put(role, string(config,
                    "scoreboard.role-names." + roleKey(role), role.name()));
        }
        lines = Map.copyOf(parsedLines);
        roleNames = Map.copyOf(parsedRoleNames);
        fullLife = string(config, "scoreboard.lives.full", "<red>♥");
        emptyLife = string(config, "scoreboard.lives.empty", "<dark_gray>♡");
        outbreakSegments = config.getInt("scoreboard.outbreak.segments", 12);
        survivorSegment = string(config, "scoreboard.outbreak.survivor-segment", "<blue>■");
        infectedSegment = string(config, "scoreboard.outbreak.infected-segment", "<red>■");
        emptySegment = string(config, "scoreboard.outbreak.empty-segment", "<dark_gray>■");
    }

    public List<String> lines(ParticipantRole role) {
        return lines.get(role);
    }

    public Map<String, String> placeholders(State state) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("role", roleNames.get(state.role()));
        placeholders.put("kills", Integer.toString(state.kills()));
        placeholders.put("infections", Integer.toString(state.infections()));
        placeholders.put("lives", Integer.toString(state.remainingLives()));
        placeholders.put("max_lives", Integer.toString(state.maxLives()));
        placeholders.put("lives_hearts", lives(state.remainingLives(), state.maxLives()));
        placeholders.put("survivors", Integer.toString(state.survivors()));
        placeholders.put("infected", Integer.toString(state.infected()));
        placeholders.put("round_mode", state.roundMode());
        placeholders.put("time_remaining", state.timeRemaining());
        placeholders.put("outbreak_bar", ScoreboardBar.build(
                state.survivors(),
                state.infected(),
                outbreakSegments,
                survivorSegment,
                infectedSegment,
                emptySegment
        ));
        return Map.copyOf(placeholders);
    }

    private String lives(int remaining, int maximum) {
        int safeMaximum = Math.min(InfectedLifeTracker.MAX_LIVES, Math.max(0, maximum));
        int safeRemaining = Math.max(0, Math.min(remaining, safeMaximum));
        return fullLife.repeat(safeRemaining) + emptyLife.repeat(safeMaximum - safeRemaining);
    }

    private static String roleKey(ParticipantRole role) {
        return switch (role) {
            case SURVIVOR -> "survivor";
            case INFECTED -> "infected";
            case NONE -> "spectator";
        };
    }

    private static String string(ConfigurationSection config, String path, String fallback) {
        return Objects.requireNonNullElse(config.getString(path, fallback), fallback);
    }

    public record State(
            ParticipantRole role,
            int kills,
            int infections,
            int remainingLives,
            int maxLives,
            int survivors,
            int infected,
            String roundMode,
            String timeRemaining
    ) {
        public State {
            Objects.requireNonNull(role, "role");
            roundMode = Objects.requireNonNullElse(roundMode, "Deathmatch");
            timeRemaining = Objects.requireNonNullElse(timeRemaining, "No Limit");
        }
    }
}
