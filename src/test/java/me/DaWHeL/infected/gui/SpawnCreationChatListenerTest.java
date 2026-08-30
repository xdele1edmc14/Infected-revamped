package me.DaWHeL.infected.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.DaWHeL.infected.InfectedPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnCreationChatListenerTest {
    private InfectedPlugin plugin;
    private SpawnCreationSessionManager sessions;
    private BukkitScheduler scheduler;
    private SpawnCreationChatListener listener;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        sessions = mock(SpawnCreationSessionManager.class);
        scheduler = mock(BukkitScheduler.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        listener = new SpawnCreationChatListener(plugin, sessions);
        player = mock(Player.class);
    }

    @Test
    void pendingChatIsHiddenAndCapturedOnlyOnTheMainThread() {
        when(sessions.hasSession(player)).thenReturn(true);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text("arena_north"));

        listener.onAsyncChat(event);

        verify(event).setCancelled(true);
        verify(sessions, never()).capture(any(), any());
        ArgumentCaptor<Runnable> bridge = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), bridge.capture());
        bridge.getValue().run();
        verify(sessions).capture(player, "arena_north");
    }

    @Test
    void ordinaryChatIsUntouchedAndQuitCancelsWithoutReopening() {
        AsyncChatEvent chat = mock(AsyncChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        when(sessions.hasSession(player)).thenReturn(false);

        listener.onAsyncChat(chat);
        listener.onQuit(new PlayerQuitEvent(player, "left"));

        verify(chat, never()).setCancelled(true);
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
        verify(sessions).cancel(player, false);
    }
}
