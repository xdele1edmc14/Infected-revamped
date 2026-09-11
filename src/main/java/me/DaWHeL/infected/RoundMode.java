package me.DaWHeL.infected;

import java.util.Locale;

public enum RoundMode {
    TIME_LIMIT("Time Limit", "time-limit"),
    DEATHMATCH("Deathmatch", "deathmatch");

    private final String displayName;
    private final String configKey;

    RoundMode(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public String displayName() {
        return displayName;
    }

    public String configKey() {
        return configKey;
    }

    public RoundMode next() {
        return this == TIME_LIMIT ? DEATHMATCH : TIME_LIMIT;
    }

    public static RoundMode parse(String value) {
        if (value == null) {
            return DEATHMATCH;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return DEATHMATCH;
        }
    }
}
