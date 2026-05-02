package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class CombatManager {

    private final KoalaCombat plugin;
    private final Map<UUID, Long> combatExpiry = new HashMap<>();
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>();
    // Track ALL opponents per player (not just last one)
    private final Map<UUID, Set<UUID>> combatOpponents = new HashMap<>();
    private final Set<UUID> kickedPlayers = new HashSet<>();

    public CombatManager(KoalaCombat plugin) {
        this.plugin = plugin;
    }

    public boolean isInCombat(UUID uuid) {
        Long expiry = combatExpiry.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            endCombat(uuid, true);
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(UUID uuid) {
        Long expiry = combatExpiry.get(uuid);
        if (expiry == null) return 0;
        return (int) Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void tagPlayers(Player attacker, Player victim) {
        tagPlayer(attacker);
        tagPlayer(victim);
        // Track both as opponents of each other
        combatOpponents.computeIfAbsent(attacker.getUniqueId(), k -> new HashSet<>()).add(victim.getUniqueId());
        combatOpponents.computeIfAbsent(victim.getUniqueId(), k -> new HashSet<>()).add(attacker.getUniqueId());
    }

    public void tagPlayer(Player player) {
        int duration = plugin.getConfig().getInt("combat-duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);
        boolean wasInCombat = isInCombat(player.getUniqueId());
        combatExpiry.put(player.getUniqueId(), expiry);

        if (player.isGliding()) player.setGliding(false);

        if (!wasInCombat) {
            String msg = plugin.getConfig().getString("tagged-message", "&c⚔ You are now in combat!")
                .replace("&", "§");
            player.sendMessage(msg);
            startActionBar(player);
        }
    }

    public void endCombat(UUID uuid, boolean natural) {
        combatExpiry.remove(uuid);
        stopActionBar(uuid);
        combatOpponents.remove(uuid);

        if (natural) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String msg = plugin.getConfig().getString("combat-end-message", "&aYou are no longer in combat.")
                    .replace("&", "§");
                player.sendMessage(msg);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(""));
            }
        }
    }

    /**
     * Called when a player dies — ends their combat AND checks all opponents.
     * If an opponent has no other combat targets left, their combat ends too.
     */
    public void handleDeath(UUID deadPlayer) {
    endCombat(deadPlayer, false);

    Set<UUID> opponents = combatOpponents.remove(deadPlayer);
    if (opponents == null) return;

    for (UUID opponentUUID : opponents) {
        // End combat for anyone who was fighting the dead player
        endCombat(opponentUUID, true);
        
        // Remove dead player from opponent's target list
        Set<UUID> opponentTargets = combatOpponents.get(opponentUUID);
        if (opponentTargets != null) {
            opponentTargets.remove(deadPlayer);
        }
    }
}

    public void markKicked(UUID uuid) { kickedPlayers.add(uuid); }
    public boolean isKicked(UUID uuid) { return kickedPlayers.contains(uuid); }
    public void clearKicked(UUID uuid) { kickedPlayers.remove(uuid); }

    public void handleCombatLog(Player player) {
        if (!isInCombat(player.getUniqueId())) return;
        if (isKicked(player.getUniqueId())) {
            clearKicked(player.getUniqueId());
            endCombat(player.getUniqueId(), false);
            return;
        }

        String broadcast = plugin.getConfig().getString("combatlog-broadcast",
            "&c{player} &7tried to escape combat and &cdied!")
            .replace("{player}", player.getName())
            .replace("&", "§");
        Bukkit.broadcastMessage(broadcast);

        // End all opponents' combat since this player is gone
        Set<UUID> opponents = combatOpponents.get(player.getUniqueId());
        combatExpiry.remove(player.getUniqueId());
        stopActionBar(player.getUniqueId());
        combatOpponents.remove(player.getUniqueId());

        if (opponents != null) {
            for (UUID opponentUUID : opponents) {
                Set<UUID> opponentTargets = combatOpponents.get(opponentUUID);
                if (opponentTargets != null) {
                    opponentTargets.remove(player.getUniqueId());
                    if (opponentTargets.isEmpty()) endCombat(opponentUUID, true);
                }
            }
        }

        player.setHealth(0);
    }

    private void startActionBar(Player player) {
        stopActionBar(player.getUniqueId());
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { stopActionBar(player.getUniqueId()); return; }
            int remaining = getRemainingSeconds(player.getUniqueId());
            if (remaining <= 0) { endCombat(player.getUniqueId(), true); return; }
            String msg = plugin.getConfig().getString("action-bar", "&c⚔ In Combat: &f{time}s")
                .replace("{time}", String.valueOf(remaining))
                .replace("&", "§");
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(msg));
        }, 0L, 20L).getTaskId();
        actionBarTasks.put(player.getUniqueId(), taskId);
    }

    private void stopActionBar(UUID uuid) {
        Integer taskId = actionBarTasks.remove(uuid);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    public void cleanup() {
        for (UUID uuid : new HashSet<>(combatExpiry.keySet())) stopActionBar(uuid);
        combatExpiry.clear();
        combatOpponents.clear();
    }
}
