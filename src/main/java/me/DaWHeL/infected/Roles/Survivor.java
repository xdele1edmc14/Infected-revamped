package me.DaWHeL.infected.Roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Survivor {
    private final Player player;

    public Survivor(Player player) {
        this.player = player;
        setup();
    }

    private void setup() {
        player.setGlowing(false);
        player.getInventory().setHelmet(null);
        player.setPlayerListName(ChatColor.GREEN + player.getName());
        player.sendMessage(ChatColor.GREEN + "You are a survivor! Avoid the zombies!");
    }

    public Player getPlayer() {
        return player;
    }
}
