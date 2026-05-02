package com.veysure.rbw.ingame.sync.listeners;

import com.veysure.rbw.ingame.sync.RoleSync;
import com.veysure.rbw.ingame.sync.RolesSyncBot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {

    private final RolesSyncBot plugin;

    public PlayerEventListener(RolesSyncBot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            RoleSync roleSync = plugin.getRoleSync();
            if (roleSync == null) {
                plugin.getLogger().warning("RoleSync not ready yet when " + player.getName() + " joined — skipping sync.");
                return;
            }

            roleSync.syncPlayer(player);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        RoleSync roleSync = plugin.getRoleSync();
        if (roleSync != null) {
            roleSync.clearCache(event.getPlayer().getUniqueId());
        }
    }
}
