package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

public final class MatchPresentationService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final InfectedPlugin plugin;
    private final SoundResolver soundResolver;

    public MatchPresentationService(InfectedPlugin plugin) {
        this(plugin, MatchPresentationService::soundExists);
    }

    MatchPresentationService(InfectedPlugin plugin, SoundResolver soundResolver) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.soundResolver = Objects.requireNonNull(soundResolver, "soundResolver");
    }

    public void countdown(Collection<Player> participants, int seconds) {
        if (seconds < 1 || seconds > 3) {
            return;
        }
        String path = "presentation.countdown";
        float pitchBase = floatValue(path + ".pitch-base", 0.8f);
        float pitchStep = floatValue(path + ".pitch-step", 0.2f);
        float pitch = pitchBase + ((3 - seconds) * pitchStep);
        present(
                participants,
                text(path + ".title", "&e{time}").replace("{time}", String.valueOf(seconds)),
                text(path + ".subtitle", ""),
                path,
                pitch
        );
    }

    public void deployment(Collection<Player> participants) {
        String path = "presentation.deployment";
        present(
                participants,
                text(path + ".title", "&eGET READY"),
                text(path + ".subtitle", "&7Survivors are deploying"),
                path,
                floatValue(path + ".pitch", 1.0f)
        );
    }

    public void active(
            Collection<Player> survivors,
            Collection<Player> infected,
            Collection<Player> spectators
    ) {
        presentActiveRole(survivors, "survivor", "&aSURVIVE!", "&7Stay alive and avoid infection");
        presentActiveRole(infected, "infected", "&cHUNT!", "&7Infect every survivor");
        presentActiveRole(spectators, "spectator", "&eROUND STARTED", "&7You are queued for the next round");
    }

    private void presentActiveRole(
            Collection<Player> players,
            String role,
            String defaultTitle,
            String defaultSubtitle
    ) {
        String rolePath = "presentation.active." + role;
        present(
                players,
                text(rolePath + ".title", defaultTitle),
                text(rolePath + ".subtitle", defaultSubtitle),
                "presentation.active",
                floatValue("presentation.active.pitch", 1.0f)
        );
    }

    private void present(
            Collection<Player> players,
            String titleText,
            String subtitleText,
            String settingsPath,
            float pitch
    ) {
        if (players == null || players.isEmpty()) {
            return;
        }
        Title title = Title.title(
                component(titleText),
                component(subtitleText),
                Title.Times.times(
                        ticks(settingsPath + ".fade-in-ticks", 5),
                        ticks(settingsPath + ".stay-ticks", 30),
                        ticks(settingsPath + ".fade-out-ticks", 10)
                )
        );
        String sound = configuredSound(settingsPath + ".sound");
        float volume = floatValue(settingsPath + ".volume", 1.0f);
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.showTitle(title);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private String configuredSound(String path) {
        String configured = text(path, defaultSound(path));
        if (configured.isBlank()) {
            return null;
        }
        String normalized = configured.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (!soundResolver.isValid(normalized)) {
            plugin.getLogger().warning("Invalid match presentation sound '" + configured
                    + "' at " + path + "; skipping the sound.");
            return null;
        }
        return normalized;
    }

    private String defaultSound(String path) {
        if (path.startsWith("presentation.countdown")) {
            return "BLOCK_NOTE_BLOCK_HAT";
        }
        if (path.startsWith("presentation.deployment")) {
            return "BLOCK_BEACON_ACTIVATE";
        }
        return "ENTITY_ENDER_DRAGON_GROWL";
    }

    private String text(String path, String fallback) {
        return config().getString(path, fallback);
    }

    private float floatValue(String path, float fallback) {
        return (float) config().getDouble(path, fallback);
    }

    private Duration ticks(String path, int fallback) {
        return Duration.ofMillis(Math.max(0, config().getInt(path, fallback)) * 50L);
    }

    private Component component(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText);
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private static boolean soundExists(String configured) {
        NamespacedKey key = NamespacedKey.fromString(configured);
        return key != null && Registry.SOUNDS.get(key) != null;
    }

    @FunctionalInterface
    interface SoundResolver {
        boolean isValid(String configuredName);
    }
}
