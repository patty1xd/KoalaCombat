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
import org.bukkit.inventory.meta.ItemMeta;

public class CombatListener implements Listener {

    private final KoalaCombat plugin;

    public CombatListener(KoalaCombat plugin) { this.plugin = plugin; }

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

        // Don't tag if either is in a safe zone
        if (plugin.getSafeZoneManager().isSafeZone(attacker.getLocation())) return;
        if (plugin.getSafeZoneManager().isSafeZone(victim.getLocation())) return;

        // Don't tag teammates or allies (TeamsPlugin integration)
        if (areTeammates(attacker, victim)) return;

        // Mace cooldown check
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.MACE) {
            if (!plugin.getCooldownManager().checkAndApply("mace", attacker)) {
                event.setCancelled(true);
                return;
            }
            if (plugin.getConfig().getBoolean("cooldowns.mace.enabled", true)) {
                int seconds = plugin.getConfig().getInt("cooldowns.mace.seconds", 8);
                attacker.setCooldown(Material.MACE, seconds * 20);
            }
        }

        plugin.getCombatManager().tagPlayer(attacker);
        plugin.getCombatManager().tagPlayer(victim);
    }

    // Detect kicks/bans
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        plugin.getCombatManager().markKicked(event.getPlayer().getUniqueId());
    }

    // Combat log
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            plugin.getCombatManager().handleCombatLog(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().endCombat(event.getEntity().getUniqueId(), false);
        plugin.getCooldownManager().clearPlayer(event.getEntity().getUniqueId());
    }

    // Block commands — OP bypass
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
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

    // Block safe zone entry + barrier particles
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
                "&cYou cannot enter a safe zone while in combat!").replace("&", "§");
            player.sendMessage(msg);
            showBarrierParticles(player, event.getTo());
        }
    }

    private void showBarrierParticles(Player player, Location loc) {
        try {
            // Use DUST particles in red color — works reliably in all 1.21 versions
            Particle.DustOptions dust = new Particle.DustOptions(
                org.bukkit.Color.RED, 1.5f);
            for (double x = -1.5; x <= 1.5; x += 0.4) {
                for (double y = 0; y <= 2.5; y += 0.4) {
                    Location particleLoc = loc.clone().add(x, y, 0);
                    player.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
                }
            }
        } catch (Exception ignored) {}
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

    // Block trident throw AND riptide in combat
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

    // Block riptide specifically — fires when player uses riptide to launch themselves
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        // Not needed for riptide — handled below via interact
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        switch (item.getType()) {
            case ENDER_PEARL -> {
                if (!plugin.getCooldownManager().checkAndApply("ender-pearl", player)) {
                    event.setCancelled(true);
                } else if (plugin.getConfig().getBoolean("cooldowns.ender-pearl.enabled", true)) {
                    int seconds = plugin.getConfig().getInt("cooldowns.ender-pearl.seconds", 15);
                    player.setCooldown(Material.ENDER_PEARL, seconds * 20);
                }
            }
            case TRIDENT -> {
                // Block riptide — check if trident has riptide enchant
                if (!plugin.getConfig().getBoolean("items.trident.enabled", true)) break;
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasEnchant(org.bukkit.enchantments.Enchantment.RIPTIDE)) {
                    event.setCancelled(true);
                    String msg = plugin.getConfig().getString("items.trident.message",
                        "&cYou cannot use a Trident while in combat!").replace("&", "§");
                    player.sendMessage(msg);
                }
            }
        }
    }

    // Totem cooldown
    @EventHandler
    public void onTotemUse(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getCooldownManager().checkAndApply("totem", player)) {
            event.setCancelled(true);
        } else if (plugin.getConfig().getBoolean("cooldowns.totem.enabled", true)) {
            int seconds = plugin.getConfig().getInt("cooldowns.totem.seconds", 10);
            player.setCooldown(Material.TOTEM_OF_UNDYING, seconds * 20);
        }
    }

    // Check if two players are teammates or allies via TeamsPlugin (reflection - no compile dependency)
    private boolean areTeammates(Player a, Player b) {
        try {
            org.bukkit.plugin.Plugin teamsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("TeamsPlugin");
            if (teamsPlugin == null) return false;

            // Use reflection to avoid compile dependency
            Object teamManager = teamsPlugin.getClass().getMethod("getTeamManager").invoke(teamsPlugin);
            Object teamA = teamManager.getClass().getMethod("getPlayerTeam", java.util.UUID.class)
                .invoke(teamManager, a.getUniqueId());
            Object teamB = teamManager.getClass().getMethod("getPlayerTeam", java.util.UUID.class)
                .invoke(teamManager, b.getUniqueId());

            if (teamA == null || teamB == null) return false;
            if (teamA == teamB) return true;

            // Check allies
            Boolean allied = (Boolean) teamManager.getClass()
                .getMethod("areAllies", teamA.getClass().getSuperclass(), teamB.getClass().getSuperclass())
                .invoke(teamManager, teamA, teamB);
            return allied != null && allied;
        } catch (Exception e) {
            return false;
        }
    }
}
