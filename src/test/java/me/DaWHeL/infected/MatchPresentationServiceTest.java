package me.DaWHeL.infected;

import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchPresentationServiceTest {
    private YamlConfiguration config;
    private Logger logger;
    private MatchPresentationService presentation;

    @BeforeEach
    void setUp() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        config = new YamlConfiguration();
        logger = mock(Logger.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        presentation = new MatchPresentationService(plugin,
                configured -> !configured.equals("minecraft:not_a_real_sound"));
    }

    @Test
    void finalThreeCountdownCuesRiseInPitchAndIgnoreEarlierSeconds() {
        config.set("presentation.countdown.sound", "BLOCK_NOTE_BLOCK_HAT");
        config.set("presentation.countdown.volume", 0.75);
        config.set("presentation.countdown.pitch-base", 0.8);
        config.set("presentation.countdown.pitch-step", 0.2);
        Player participant = player("countdown-participant");

        presentation.countdown(List.of(participant), 4);
        verify(participant, never()).showTitle(any(Title.class));
        verify(participant, never()).playSound(any(Location.class), any(String.class),
                anyFloat(), anyFloat());

        presentation.countdown(List.of(participant), 3);
        presentation.countdown(List.of(participant), 2);
        presentation.countdown(List.of(participant), 1);

        ArgumentCaptor<Float> pitch = ArgumentCaptor.forClass(Float.class);
        verify(participant, org.mockito.Mockito.times(3)).playSound(
                any(Location.class), eq("minecraft:block_note_block_hat"), eq(0.75f), pitch.capture());
        assertEquals(List.of(0.8f, 1.0f, 1.2f), pitch.getAllValues());
        verify(participant, org.mockito.Mockito.times(3)).showTitle(any(Title.class));
    }

    @Test
    void deploymentPresentsOnlyTheLockedParticipants() {
        Player first = player("first");
        Player second = player("second");
        Player unrelated = player("unrelated");

        presentation.deployment(List.of(first, second));

        verify(first).showTitle(any(Title.class));
        verify(second).showTitle(any(Title.class));
        verify(unrelated, never()).showTitle(any(Title.class));
    }

    @Test
    void activePresentationUsesRoleSpecificTitles() {
        config.set("presentation.active.survivor.title", "&aSURVIVE!");
        config.set("presentation.active.infected.title", "&cHUNT!");
        config.set("presentation.active.spectator.title", "&eROUND STARTED");
        Player survivor = player("survivor");
        Player infected = player("infected");
        Player spectator = player("spectator");

        presentation.active(List.of(survivor), List.of(infected), List.of(spectator));

        assertTitle(survivor, "\u00a7aSURVIVE!");
        assertTitle(infected, "\u00a7cHUNT!");
        assertTitle(spectator, "\u00a7eROUND STARTED");
    }

    @Test
    void invalidSoundIsLoggedAndDoesNotSuppressTheTitle() {
        config.set("presentation.deployment.sound", "NOT_A_REAL_SOUND");
        Player participant = player("invalid-sound");

        presentation.deployment(List.of(participant));

        verify(participant).showTitle(any(Title.class));
        verify(participant, never()).playSound(any(Location.class), any(String.class),
                anyFloat(), anyFloat());
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("NOT_A_REAL_SOUND"));
    }

    @Test
    void defaultSurvivorSubtitleDoesNotPromiseAnOptionalTimeLimit() {
        Player survivor = player("survivor-copy");

        presentation.active(List.of(survivor), List.of(), List.of());

        ArgumentCaptor<Title> title = ArgumentCaptor.forClass(Title.class);
        verify(survivor).showTitle(title.capture());
        String subtitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(title.getValue().subtitle());
        assertFalse(subtitle.toLowerCase(java.util.Locale.ROOT).contains("time"));
        assertFalse(subtitle.toLowerCase(java.util.Locale.ROOT).contains("expire"));
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(mock(Location.class));
        return player;
    }

    private static void assertTitle(Player player, String expectedLegacyTitle) {
        ArgumentCaptor<Title> title = ArgumentCaptor.forClass(Title.class);
        verify(player).showTitle(title.capture());
        String actual = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(title.getValue().title());
        assertEquals(expectedLegacyTitle, actual);
    }
}
