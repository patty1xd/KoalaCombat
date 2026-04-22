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
    // Track who is fighting who so we can end both combats on death
    private final Map<UUID, UUID> combatOpponent = new HashMap<>();
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
        // Track opponents
        combatOpponent.put(attacker.getUniqueId(), victim.getUniqueId());
        combatOpponent.put(victim.getUniqueId(), attacker.getUniqueId());
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
        combatOpponent.remove(uuid);

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

    // Called when a player dies — also ends their opponent's combat
    public void handleDeath(UUID deadPlayer) {
        // End the dead player's combat
        endCombat(deadPlayer, false);

        // End the opponent's combat too
        UUID opponent = combatOpponent.get(deadPlayer);
        if (opponent != null) {
            endCombat(opponent, true);
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

        // End opponent's combat too
        UUID opponent = combatOpponent.get(player.getUniqueId());
        combatExpiry.remove(player.getUniqueId());
        stopActionBar(player.getUniqueId());
        combatOpponent.remove(player.getUniqueId());

        if (opponent != null) {
            endCombat(opponent, true);
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
    }
}
