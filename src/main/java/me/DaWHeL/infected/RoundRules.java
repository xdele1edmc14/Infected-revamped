package me.DaWHeL.infected;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Objects;

public record RoundRules(
        RoundMode mode,
        int startingInfected,
        int infectedLives,
        int timeLimitSeconds,
        boolean bossBarEnabled,
        String bossBarTitle,
        BarColor bossBarColor,
        BarStyle bossBarStyle,
        boolean trackingCompassEnabled,
        int trackingCompassActivationSeconds,
        int trackingCompassDisableBelowInfected
) {
    private static final int DEFAULT_STARTING_INFECTED = 5;
    private static final int DEFAULT_INFECTED_LIVES = 3;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 600;
    private static final String DEFAULT_BOSS_BAR_TITLE = "&eTime Remaining: &f{time}";

    public RoundRules {
        Objects.requireNonNull(mode, "mode");
    }

    public static RoundRules from(ConfigurationSection config, RoundMode mode) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mode, "mode");
        String modePath = "settings.modes." + mode.configKey();
        int legacyStarting = config.getInt("settings.starting-zombies", DEFAULT_STARTING_INFECTED);
        int startingInfected = integer(config, modePath + ".starting-zombies", legacyStarting);
        if (mode == RoundMode.TIME_LIMIT) {
            int seconds = Math.max(1, integer(config,
                    modePath + ".time-limit-seconds", DEFAULT_TIME_LIMIT_SECONDS));
            return new RoundRules(
                    mode,
                    startingInfected,
                    1,
                    seconds,
                    config.getBoolean(modePath + ".boss-bar.enabled", true),
                    config.getString(modePath + ".boss-bar.title", DEFAULT_BOSS_BAR_TITLE),
                    enumValue(config.getString(modePath + ".boss-bar.color"), BarColor.YELLOW),
                    enumValue(config.getString(modePath + ".boss-bar.style"), BarStyle.SOLID),
                    config.getBoolean(modePath + ".tracking-compass.enabled", true),
                    Math.max(0, integer(config,
                            modePath + ".tracking-compass.give-below-minutes", 5)) * 60,
                    0
            );
        }

        int legacyLives = config.getInt("settings.infected-lives", DEFAULT_INFECTED_LIVES);
        int lives = Math.min(InfectedLifeTracker.MAX_LIVES,
                Math.max(1, integer(config, modePath + ".infected-lives", legacyLives)));
        return new RoundRules(
                mode,
                startingInfected,
                lives,
                0,
                false,
                DEFAULT_BOSS_BAR_TITLE,
                BarColor.YELLOW,
                BarStyle.SOLID,
                config.getBoolean(modePath + ".tracking-compass.enabled", true),
                0,
                Math.max(1, integer(config,
                        modePath + ".tracking-compass.remove-below-zombies", 10))
        );
    }

    public boolean hasTimeLimit() {
        return mode == RoundMode.TIME_LIMIT;
    }

    private static int integer(ConfigurationSection config, String path, int fallback) {
        return config.contains(path, true) ? config.getInt(path) : fallback;
    }

    private static <E extends Enum<E>> E enumValue(String configured, E fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
