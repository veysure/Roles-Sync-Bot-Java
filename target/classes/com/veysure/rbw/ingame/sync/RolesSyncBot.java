package com.veysure.rbw.ingame.sync;

import com.veysure.rbw.ingame.sync.commands.RoleSyncCommand;
import com.veysure.rbw.ingame.sync.listeners.PlayerEventListener;
import com.veysure.rbw.ingame.sync.utils.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RolesSyncBot extends JavaPlugin {

    private JDA jda;
    private RoleSync roleSync;
    private boolean debugMode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debugMode = getConfig().getBoolean("debug", false);

        String token = getConfig().getString("discord-bot-token");
        String guildId = getConfig().getString("discord-guild-id");

        if (token == null || token.isEmpty()) {
            getLogger().severe("discord-bot-token is not set in config.yml! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (guildId == null || guildId.isEmpty()) {
            getLogger().severe("discord-guild-id is not set in config.yml! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LuckPerms luckPerms;
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms is not loaded! Make sure it's installed and listed as a dependency.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DatabaseManager.init(this);

        getLogger().info("Connecting to Discord in the background...");

        final String finalToken = token;
        final String finalGuildId = guildId;
        final LuckPerms finalLuckPerms = luckPerms;

        Thread startupThread = new Thread(() -> {
            try {
                JDA builtJda = JDABuilder.createDefault(finalToken)
                        .enableIntents(GatewayIntent.GUILD_MEMBERS)
                        .build()
                        .awaitReady();

                Guild guild = builtJda.getGuildById(finalGuildId);
                if (guild == null) {
                    getLogger().severe("Could not find a guild with ID: " + finalGuildId + ". Make sure the bot is in that server.");
                    builtJda.shutdown();
                    // Disable must happen on the main thread
                    getServer().getScheduler().runTask(this, () ->
                            getServer().getPluginManager().disablePlugin(this));
                    return;
                }

                getServer().getScheduler().runTask(this, () -> finishStartup(builtJda, guild, finalLuckPerms));

            } catch (Exception e) {
                getLogger().severe("Failed to connect to Discord. Double-check your bot token.");
                getLogger().severe("Error: " + e.getMessage());
                getServer().getScheduler().runTask(this, () ->
                        getServer().getPluginManager().disablePlugin(this));
            }
        }, "RolesSyncBot-Startup");

        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void finishStartup(JDA builtJda, Guild guild, LuckPerms luckPerms) {
        this.jda = builtJda;
        this.roleSync = new RoleSync(this, luckPerms, guild);

        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        getCommand("rolesync").setExecutor(new RoleSyncCommand(this));

        getLogger().info("Successfully connected to Discord guild: " + guild.getName());
        getLogger().info("RolesSyncBot is now running.");

        if (debugMode) {
            getLogger().info("[Debug] Debug mode is enabled.");
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
            getLogger().info("Disconnected from Discord.");
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public RoleSync getRoleSync() {
        return roleSync;
    }

    public Guild getDiscordGuild() {
        return jda != null ? jda.getGuildById(getConfig().getString("discord-guild-id", "")) : null;
    }
}
