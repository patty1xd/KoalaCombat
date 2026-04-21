package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CombatManager {

    private final KoalaCombat plugin;
    private final Map<UUID, Long> combatExpiry = new HashMap<>();
    private final Map<UUID, Integer> actionBarTasks = new HashMap<>();
    private final Set<UUID> markedForDeath = new HashSet<>();

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

    public void tagPlayer(Player player) {
        int duration = plugin.getConfig().getInt("combat-duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);
        boolean wasInCombat = isInCombat(player.getUniqueId());
        combatExpiry.put(player.getUniqueId(), expiry);

        // If player is gliding with elytra, stop them immediately
        if (player.isGliding()) {
            player.setGliding(false);
        }

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

    public void handleCombatLog(Player player) {
        if (!isInCombat(player.getUniqueId())) return;

        String broadcast = plugin.getConfig().getString("combatlog-broadcast", "&c{player} &7tried to escape combat and &cdied!")
            .replace("{player}", player.getName())
            .replace("&", "§");
        Bukkit.broadcastMessage(broadcast);

        combatExpiry.remove(player.getUniqueId());
        stopActionBar(player.getUniqueId());

        // Drop all inventory items at their location
        Location loc = player.getLocation();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() != org.bukkit.Material.AIR) {
            loc.getWorld().dropItemNaturally(loc, offhand);
        }

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);

        // Mark for death on rejoin (sets health to 0 so death screen shows)
        markedForDeath.add(player.getUniqueId());
    }

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
        for (UUID uuid : new HashSet<>(combatExpiry.keySet())) stopActionBar(uuid);
        combatExpiry.clear();
    }
}
