package com.koalacombat.listeners;

import com.koalacombat.KoalaCombat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {

    private final KoalaCombat plugin;

    public CombatListener(KoalaCombat plugin) { this.plugin = plugin; }

    // Tag players on PvP hit
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
            && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker == victim) return;

        plugin.getCombatManager().tagPlayer(attacker);
        plugin.getCombatManager().tagPlayer(victim);
    }

    // Combat log on disconnect
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            plugin.getCombatManager().handleCombatLog(player);
        }
    }

    // Kill on rejoin if they somehow survived the disconnect (edge case)
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isMarkedForDeath(player.getUniqueId())) {
            plugin.getCombatManager().clearDeathMark(player.getUniqueId());
            player.setHealth(0);
        }
    }

    // Remove from combat on death
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().endCombat(event.getEntity().getUniqueId(), false);
        plugin.getCooldownManager().clearPlayer(event.getEntity().getUniqueId());
    }

    // Block commands while in combat
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;

        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

        // Check whitelist
        java.util.List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        for (String a : allowed) {
            if (command.equalsIgnoreCase(a)) return;
        }

        // Block the command
        event.setCancelled(true);
        String msg = plugin.getConfig().getString("blocked-command-message", "&cYou cannot use &f/{cmd} &cwhile in combat!")
            .replace("{cmd}", command)
            .replace("&", "§");
        player.sendMessage(msg);
    }

    // Block safe zone entry while in combat
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        // Only check on block change
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        if (plugin.getSafeZoneManager().isSafeZone(event.getTo())) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("safezone-message", "&cYou cannot enter a safe zone while in combat!")
                .replace("&", "§");
            player.sendMessage(msg);
        }
    }

    // Block elytra use in combat
    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("items.elytra.enabled", true)) return;
        if (event.isGliding()) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("items.elytra.message", "&cYou cannot use an Elytra while in combat!")
                .replace("&", "§");
            player.sendMessage(msg);
        }
    }

    // Block trident throw in combat
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("items.trident.enabled", true)) return;
        event.setCancelled(true);
        String msg = plugin.getConfig().getString("items.trident.message", "&cYou cannot throw a Trident while in combat!")
            .replace("&", "§");
        player.sendMessage(msg);
    }

    // Item cooldowns in combat (ender pearl, totem, mace)
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        switch (item.getType()) {
            case ENDER_PEARL -> {
                if (!plugin.getCooldownManager().checkAndApply("ender-pearl", player)) {
                    event.setCancelled(true);
                }
            }
            case MACE -> {
                if (!plugin.getCooldownManager().checkAndApply("mace", player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Totem cooldown on use
    @EventHandler
    public void onTotemUse(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getCooldownManager().checkAndApply("totem", player)) {
            event.setCancelled(true);
        }
    }
}
