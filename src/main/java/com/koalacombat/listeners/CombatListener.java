package com.koalacombat.listeners;

import com.koalacombat.KoalaCombat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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

    // Tag players on PvP hit — skip if attacker is in safe zone
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

        // Don't tag if attacker is in a safe zone
        if (plugin.getSafeZoneManager().isSafeZone(attacker.getLocation())) return;
        // Don't tag if victim is in a safe zone
        if (plugin.getSafeZoneManager().isSafeZone(victim.getLocation())) return;

        // Mace cooldown check
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.MACE) {
            if (!plugin.getCooldownManager().checkAndApply("mace", attacker)) {
                event.setCancelled(true);
                return;
            }
            // Apply visual hotbar cooldown using Bukkit API
            if (plugin.getConfig().getBoolean("cooldowns.mace.enabled", true)) {
                int seconds = plugin.getConfig().getInt("cooldowns.mace.seconds", 8);
                attacker.setCooldown(Material.MACE, seconds * 20);
            }
        }

        plugin.getCombatManager().tagPlayer(attacker);
        plugin.getCombatManager().tagPlayer(victim);
    }

    // Detect kicks/bans so we don't kill them
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        plugin.getCombatManager().markKicked(event.getPlayer().getUniqueId());
    }

    // Combat log — fire BEFORE the player fully disconnects
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            plugin.getCombatManager().handleCombatLog(player);
        }
    }

    // Remove from combat on death
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().endCombat(event.getEntity().getUniqueId(), false);
        plugin.getCooldownManager().clearPlayer(event.getEntity().getUniqueId());
    }

    // Block commands while in combat — OP bypass allowed
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;

        // OPs bypass command blocking
        if (player.isOp()) return;

        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

        java.util.List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        for (String a : allowed) {
            if (command.equalsIgnoreCase(a)) return;
        }

        event.setCancelled(true);
        String msg = plugin.getConfig().getString("blocked-command-message",
            "&cYou cannot use &f/{cmd} &cwhile in combat!")
            .replace("{cmd}", command).replace("&", "§");
        player.sendMessage(msg);
    }

    // Block safe zone entry — show red barrier particles on border
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        if (plugin.getSafeZoneManager().isSafeZone(event.getTo())) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("safezone-message",
                "&cYou cannot enter a safe zone while in combat!")
                .replace("&", "§");
            player.sendMessage(msg);
            // Show red barrier particles at boundary
            showBarrierParticles(player, event.getTo());
        }
    }

    private void showBarrierParticles(Player player, Location loc) {
        // Show red barrier block particles in a wall pattern
        for (double x = -1.5; x <= 1.5; x += 0.5) {
            for (double y = 0; y <= 2; y += 0.5) {
                Location particleLoc = loc.clone().add(x, y, 0);
                player.spawnParticle(Particle.BLOCK, particleLoc, 1,
                    Material.BARRIER.createBlockData());
            }
        }
    }

    // Block elytra in combat
    @EventHandler
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("items.elytra.enabled", true)) return;
        if (event.isGliding()) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("items.elytra.message",
                "&cYou cannot use an Elytra while in combat!").replace("&", "§");
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
        String msg = plugin.getConfig().getString("items.trident.message",
            "&cYou cannot throw a Trident while in combat!").replace("&", "§");
        player.sendMessage(msg);
    }

    // Ender pearl cooldown + visual
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType() == Material.ENDER_PEARL) {
            if (!plugin.getCooldownManager().checkAndApply("ender-pearl", player)) {
                event.setCancelled(true);
            } else {
                // Apply visual cooldown
                if (plugin.getConfig().getBoolean("cooldowns.ender-pearl.enabled", true)) {
                    int seconds = plugin.getConfig().getInt("cooldowns.ender-pearl.seconds", 15);
                    player.setCooldown(Material.ENDER_PEARL, seconds * 20);
                }
            }
        }
    }

    // Totem cooldown + visual
    @EventHandler
    public void onTotemUse(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getCooldownManager().checkAndApply("totem", player)) {
            event.setCancelled(true);
        } else {
            if (plugin.getConfig().getBoolean("cooldowns.totem.enabled", true)) {
                int seconds = plugin.getConfig().getInt("cooldowns.totem.seconds", 10);
                player.setCooldown(Material.TOTEM_OF_UNDYING, seconds * 20);
            }
        }
    }
}
