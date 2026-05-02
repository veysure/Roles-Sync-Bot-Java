package com.veysure.rbw.ingame.sync.commands;

import com.veysure.rbw.ingame.sync.RoleSync;
import com.veysure.rbw.ingame.sync.RolesSyncBot;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RoleSyncCommand implements CommandExecutor {

    private final RolesSyncBot plugin;

    public RoleSyncCommand(RolesSyncBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rolesync.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUsage: /rolesync <reload|sync <player>>");
            return true;
        }

        RoleSync roleSync = plugin.getRoleSync();
        if (roleSync == null) {
            sender.sendMessage("§cRoleSync is still starting up, please wait a moment.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                roleSync.reload();
                sender.sendMessage("§aConfig reloaded successfully.");
                break;

            case "sync":
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /rolesync sync <player>");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer '" + args[1] + "' is not online.");
                    return true;
                }

                roleSync.syncPlayer(target);
                sender.sendMessage("§aTriggered role sync for " + target.getName() + ".");
                break;

            default:
                sender.sendMessage("§eUsage: /rolesync <reload|sync <player>>");
                break;
        }

        return true;
    }
}
