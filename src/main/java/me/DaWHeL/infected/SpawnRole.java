package me.DaWHeL.infected;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SpawnRole {
    SURVIVOR("survivor", "survivor", "Survivor"),
    INFECTED_RELEASE("infected-release", "release", "Infected Release"),
    INFECTED_RESPAWN("infected-respawn", "respawn", "Infected Respawn");

    private final String configKey;
    private final String commandKey;
    private final String displayName;

    SpawnRole(String configKey, String commandKey, String displayName) {
        this.configKey = configKey;
        this.commandKey = commandKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String commandKey() {
        return commandKey;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<SpawnRole> fromCommandKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(role -> role.commandKey.equals(normalized) || role.configKey.equals(normalized))
                .findFirst();
    }
}
