package me.DaWHeL.infected.localization;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeathTitleMessagesTest {
    @Test
    void loadsDeathTitlesAndTimingsFromEnglishLanguageFile() throws Exception {
        YamlConfiguration language = new YamlConfiguration();
        language.load(new StringReader("""
                infected-death:
                  title: '&6Fallen'
                  subtitle: '&7Respawning'
                  out-of-lives-subtitle: '&cNo lives remain'
                  timings:
                    fade-in: 3
                    stay: 40
                    fade-out: 9
                """));

        DeathTitleMessages messages = DeathTitleMessages.from(language);

        assertEquals("&6Fallen", messages.title());
        assertEquals("&7Respawning", messages.subtitle());
        assertEquals("&cNo lives remain", messages.outOfLivesSubtitle());
        assertEquals(3, messages.fadeIn());
        assertEquals(40, messages.stay());
        assertEquals(9, messages.fadeOut());
    }
}
