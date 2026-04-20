package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final KoalaCombat plugin;
    // item key -> uuid -> expiry time
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public CooldownManager(KoalaCombat plugin) { this.plugin = plugin; }

    public boolean isOnCooldown(String item, UUID uuid) {
        Map<UUID, Long> map = cooldowns.get(item);
        if (map == null) return false;
        Long expiry = map.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) { map.remove(uuid); return false; }
        return true;
    }

    public int getRemainingSeconds(String item, UUID uuid) {
        Map<UUID, Long> map = cooldowns.get(item);
        if (map == null) return 0;
        Long expiry = map.get(uuid);
        if (expiry == null) return 0;
        return (int) Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(String item, UUID uuid) {
        int seconds = plugin.getConfig().getInt("cooldowns." + item + ".seconds", 10);
        cooldowns.computeIfAbsent(item, k -> new HashMap<>())
            .put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean checkAndApply(String item, Player player) {
        if (!plugin.getConfig().getBoolean("cooldowns." + item + ".enabled", false)) return true;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return true;
        if (isOnCooldown(item, player.getUniqueId())) {
            int remaining = getRemainingSeconds(item, player.getUniqueId());
            String msg = plugin.getConfig().getString("cooldowns." + item + ".message", "&cOn cooldown for &f{time}s&c!")
                .replace("{time}", String.valueOf(remaining))
                .replace("&", "§");
            player.sendMessage(msg);
            return false;
        }
        setCooldown(item, player.getUniqueId());
        return true;
    }

    public void clearPlayer(UUID uuid) {
        for (Map<UUID, Long> map : cooldowns.values()) map.remove(uuid);
    }
}
