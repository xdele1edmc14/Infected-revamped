package me.DaWHeL.infected;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardTextRendererTest {
    private final ScoreboardTextRenderer renderer = new ScoreboardTextRenderer();

    @Test
    void rendersMiniMessageAfterReplacingConfiguredPlaceholders() {
        Component rendered = renderer.render("<red>Role: {role}</red>", Map.of("role", "Infected"));

        assertEquals("Role: Infected", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(NamedTextColor.RED, rendered.color());
    }

    @Test
    void preservesLegacyAmpersandFormattingForExistingConfigs() {
        Component rendered = renderer.render("&c&lKills: {kills}", Map.of("kills", "7"));

        String legacy = LegacyComponentSerializer.legacySection().serialize(rendered);
        assertEquals("§c§lKills: 7", legacy);
        assertTrue(rendered.hasDecoration(TextDecoration.BOLD));
    }
}
