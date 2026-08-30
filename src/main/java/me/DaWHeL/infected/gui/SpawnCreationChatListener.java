package me.DaWHeL.infected.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.DaWHeL.infected.InfectedPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class SpawnCreationChatListener implements Listener {
    private final InfectedPlugin plugin;
    private final SpawnCreationSessionManager sessions;

    public SpawnCreationChatListener(InfectedPlugin plugin, SpawnCreationSessionManager sessions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!sessions.hasSession(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> sessions.capture(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.cancel(event.getPlayer(), false);
    }
}
