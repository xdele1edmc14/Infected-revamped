package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.regex.Pattern;

public final class ScoreboardTextRenderer {
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("(?i)&[0-9A-FK-ORX]");

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public Component render(String template, Map<String, String> placeholders) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            rendered = rendered.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return LEGACY_FORMATTING.matcher(rendered).find()
                ? legacy.deserialize(rendered)
                : miniMessage.deserialize(rendered);
    }
}
