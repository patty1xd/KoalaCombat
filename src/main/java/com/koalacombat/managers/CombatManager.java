package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class CombatManager {

    private final KoalaCombat plugin;
    // uuid -> time when combat expires (System.currentTimeMillis)
    private final Map<UUID, Long> combatExpiry = new HashMap<>();
    // uuid -> task id for action bar updater
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>();

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
        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        return (int) Math.max(0, remaining);
    }

    public void tagPlayer(Player player) {
        int duration = plugin.getConfig().getInt("combat-duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);
        boolean wasInCombat = isInCombat(player.getUniqueId());
        combatExpiry.put(player.getUniqueId(), expiry);

        if (!wasInCombat) {
            String msg = plugin.getConfig().getString("tagged-message", "&c⚔ You are now in combat!");
            player.sendMessage(msg.replace("&", "§"));
            startActionBar(player);
        }
    }

    public void endCombat(UUID uuid, boolean natural) {
        combatExpiry.remove(uuid);
        stopActionBar(uuid);
        if (natural) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String msg = plugin.getConfig().getString("combat-end-message", "&aYou are no longer in combat.");
                player.sendMessage(msg.replace("&", "§"));
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(""));
            }
        }
    }

    public void forceKill(Player player) {
        String broadcast = plugin.getConfig().getString("combatlog-broadcast", "&c{player} &7tried to escape and &cdied!")
            .replace("{player}", player.getName())
            .replace("&", "§");
        Bukkit.broadcastMessage(broadcast);
        combatExpiry.remove(player.getUniqueId());
        stopActionBar(player.getUniqueId());
        // Kill via damage
        Bukkit.getScheduler().runTask(plugin, () -> player.setHealth(0));
    }

    public void handleCombatLog(Player player) {
        if (!isInCombat(player.getUniqueId())) return;
        String broadcast = plugin.getConfig().getString("combatlog-broadcast", "&c{player} &7tried to escape and &cdied!")
            .replace("{player}", player.getName())
            .replace("&", "§");
        Bukkit.broadcastMessage(broadcast);
        combatExpiry.remove(player.getUniqueId());
        stopActionBar(player.getUniqueId());
        // Spawn a fake player entity that immediately dies to trigger death/drops
        // We just kill the player directly using a delayed task on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setHealth(0);
            } else {
                // Player already offline — use offline kill via damaging on next login
                // Store for death on next login
                plugin.getCombatManager().markForDeath(player.getUniqueId());
            }
        });
    }

    // Players who disconnected in combat and need to die on next login
    private final Set<UUID> markedForDeath = new HashSet<>();
    public void markForDeath(UUID uuid) { markedForDeath.add(uuid); }
    public boolean isMarkedForDeath(UUID uuid) { return markedForDeath.contains(uuid); }
    public void clearDeathMark(UUID uuid) { markedForDeath.remove(uuid); }

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
        for (UUID uuid : new HashSet<>(combatExpiry.keySet())) {
            stopActionBar(uuid);
        }
        combatExpiry.clear();
    }
}
