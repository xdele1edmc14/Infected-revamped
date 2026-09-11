package me.DaWHeL.infected.localization;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public record DeathTitleMessages(
        String title,
        String subtitle,
        String outOfLivesSubtitle,
        int fadeIn,
        int stay,
        int fadeOut
) {
    private static final String RESOURCE_PATH = "lang/en_us.yml";

    public DeathTitleMessages {
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        outOfLivesSubtitle = outOfLivesSubtitle == null ? "" : outOfLivesSubtitle;
        fadeIn = Math.max(0, fadeIn);
        stay = Math.max(0, stay);
        fadeOut = Math.max(0, fadeOut);
    }

    public static DeathTitleMessages load(JavaPlugin plugin) {
        File languageFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!languageFile.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }
        return from(YamlConfiguration.loadConfiguration(languageFile));
    }

    public static DeathTitleMessages from(YamlConfiguration language) {
        return new DeathTitleMessages(
                language.getString("infected-death.title", "&c&lYOU DIED"),
                language.getString("infected-death.subtitle", "&7Respawn to begin the 3-second cooldown..."),
                language.getString("infected-death.out-of-lives-subtitle", "&4Your lives have run out!"),
                language.getInt("infected-death.timings.fade-in", 10),
                language.getInt("infected-death.timings.stay", 60),
                language.getInt("infected-death.timings.fade-out", 20)
        );
    }

    public void show(Player player, boolean hasRemainingLife) {
        player.sendTitle(color(title), color(hasRemainingLife ? subtitle : outOfLivesSubtitle),
                fadeIn, stay, fadeOut);
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
