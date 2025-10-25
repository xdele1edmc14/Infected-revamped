package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.Roles.Infected;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final GameManager gameManager;
    private final InfectedPlugin plugin;

    public PlayerJoinListener(InfectedPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only assign if game is NOT running
        if (!gameManager.isGameRunning()) {
            gameManager.resetPlayer(player);
            player.sendMessage(ChatColor.GREEN + "You joined as a Survivor!");
        } else {
            gameManager.addInfected(new Infected(plugin, player, false));
            player.sendMessage(ChatColor.GREEN + "You joined as an Infected");
        }
    }
}
