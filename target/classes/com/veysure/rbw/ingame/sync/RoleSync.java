package com.veysure.rbw.ingame.sync;

import com.veysure.rbw.ingame.sync.utils.DatabaseManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RoleSync {

    private final RolesSyncBot plugin;
    private final LuckPerms luckPerms;
    private final Guild guild;

    private final Map<Role, String> roleGroupMap = new HashMap<>();
    private final Map<UUID, String> discordIdCache = new HashMap<>();

    public RoleSync(RolesSyncBot plugin, LuckPerms luckPerms, Guild guild) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.guild = guild;
        loadRoleMappings();
    }

    public void syncPlayer(Player player) {
        String discordId = resolveDiscordId(player);

        if (discordId == null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[Debug] No Discord account linked for " + player.getName() + ", skipping sync.");
            }
            return;
        }

        Member member = guild.getMemberById(discordId);
        if (member == null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[Debug] " + player.getName() + " is linked to Discord ID " + discordId + " but is not in the guild.");
            }
            return;
        }

        UUID playerUUID = player.getUniqueId();

        luckPerms.getUserManager().loadUser(playerUUID).thenAcceptAsync(user -> {
            if (user == null) {
                plugin.getLogger().warning("LuckPerms returned null user for " + player.getName() + " (" + playerUUID + ")");
                return;
            }

            Set<String> toAdd = new HashSet<>();
            Set<String> toRemove = new HashSet<>();

            for (Map.Entry<Role, String> entry : roleGroupMap.entrySet()) {
                Role role = entry.getKey();
                String groupName = entry.getValue();

                boolean memberHasRole = member.getRoles().contains(role);
                boolean playerHasGroup = user.getNodes().stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(node -> ((InheritanceNode) node).getGroupName())
                        .anyMatch(g -> g.equalsIgnoreCase(groupName));

                if (memberHasRole && !playerHasGroup) {
                    toAdd.add(groupName);
                } else if (!memberHasRole && playerHasGroup) {
                    toRemove.add(groupName);
                }
            }

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[Debug] " + player.getName() + " is already in sync, nothing to do.");
                }
                return;
            }

            applyChanges(user, toAdd, toRemove, player.getName());
        });
    }

    public void clearCache(UUID playerUUID) {
        discordIdCache.remove(playerUUID);
    }

    public void reload() {
        loadRoleMappings();
        discordIdCache.clear();
        plugin.getLogger().info("RoleSync config reloaded.");
    }

    private void loadRoleMappings() {
        roleGroupMap.clear();

        var section = plugin.getConfig().getConfigurationSection("rolesync");
        if (section == null) {
            plugin.getLogger().warning("No 'rolesync' section found in config.yml. No roles will be synced.");
            return;
        }

        for (String roleId : section.getKeys(false)) {
            String groupName = section.getString(roleId);

            if (groupName == null || groupName.isEmpty()) {
                plugin.getLogger().warning("Role ID " + roleId + " has no group name set, skipping.");
                continue;
            }

            Role role = guild.getRoleById(roleId);
            if (role == null) {
                plugin.getLogger().warning("Could not find Discord role with ID: " + roleId + " — does it still exist?");
                continue;
            }

            roleGroupMap.put(role, groupName);

            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[Debug] Mapped role " + role.getName() + " (ID: " + roleId + ") -> LP group '" + groupName + "'");
            }
        }

        plugin.getLogger().info("Loaded " + roleGroupMap.size() + " role mapping(s).");
    }

    private void applyChanges(User user, Set<String> toAdd, Set<String> toRemove, String playerName) {
        for (String group : toAdd) {
            user.data().add(InheritanceNode.builder(group).build());
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[Debug] Adding group '" + group + "' to " + playerName);
            }
        }

        for (String group : toRemove) {
            user.data().remove(InheritanceNode.builder(group).build());
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[Debug] Removing group '" + group + "' from " + playerName);
            }
        }

        luckPerms.getUserManager().saveUser(user).thenRun(() ->
            Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getLogger().info("Synced roles for " + playerName
                        + " (+" + toAdd.size() + "/-" + toRemove.size() + ")")
            )
        );
    }

    private String resolveDiscordId(Player player) {
        UUID uuid = player.getUniqueId();

        if (discordIdCache.containsKey(uuid)) {
            return discordIdCache.get(uuid);
        }

        String discordId = DatabaseManager.getDiscordId(uuid);
        if (discordId != null) {
            discordIdCache.put(uuid, discordId);
        }

        return discordId;
    }
}
