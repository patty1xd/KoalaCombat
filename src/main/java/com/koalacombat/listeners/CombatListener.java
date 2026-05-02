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

        // Don't tag if either is in safe zone
        if (plugin.getSafeZoneManager().isSafeZone(attacker.getLocation())) return;
        if (plugin.getSafeZoneManager().isSafeZone(victim.getLocation())) return;

        // Don't tag teammates or allies
        if (areTeammates(attacker, victim)) return;

        // Mace cooldown — applies on any hit, no combat requirement
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.MACE && plugin.getConfig().getBoolean("cooldowns.mace.enabled", true)) {
            if (plugin.getCooldownManager().isOnCooldown("mace", attacker.getUniqueId())) {
                String msg = plugin.getConfig().getString("cooldowns.mace.message", "&cMace is on cooldown for &f{time}s&c!")
                    .replace("{time}", String.valueOf(plugin.getCooldownManager().getRemainingSeconds("mace", attacker.getUniqueId())))
                    .replace("&", "§");
                attacker.sendMessage(msg);
                event.setCancelled(true);
                return;
            }
            plugin.getCooldownManager().setCooldown("mace", attacker.getUniqueId());
            int seconds = plugin.getConfig().getInt("cooldowns.mace.seconds", 8);
            attacker.setCooldown(Material.MACE, seconds * 20);
        }

        // Tag both players
        plugin.getCombatManager().tagPlayers(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        plugin.getCombatManager().markKicked(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            plugin.getCombatManager().handleCombatLog(player);
        }
    }

    // Death ends BOTH players' combat
    // Death ends BOTH players' combat
    @EventHandler
public void onDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();

    // End victim's combat
    plugin.getCombatManager().handleDeath(victim.getUniqueId());
    plugin.getCooldownManager().clearPlayer(victim.getUniqueId());

    // End killer's combat too — they made the kill
    if (killer != null && killer != victim) {
        plugin.getCombatManager().endCombat(killer.getUniqueId(), true);
    }
}

    // Blocklist approach — block specific commands, allow everything else
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (player.isOp()) return;

        // Get the full command (e.g. "team home" from "/team home")
        String fullCommand = event.getMessage().substring(1).toLowerCase().trim();
        String firstWord = fullCommand.split(" ")[0];

        java.util.List<String> blocked = plugin.getConfig().getStringList("blocked-commands");
        for (String b : blocked) {
            // Match either first word or full command (for "team home" etc)
            if (firstWord.equalsIgnoreCase(b) || fullCommand.startsWith(b.toLowerCase())) {
                event.setCancelled(true);
                String msg = plugin.getConfig().getString("blocked-command-message",
                    "&cYou cannot use &f/{cmd} &cwhile in combat!")
                    .replace("{cmd}", firstWord).replace("&", "§");
                player.sendMessage(msg);
                return;
            }
        }
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
            Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.RED, 1.5f);
            for (double x = -1.5; x <= 1.5; x += 0.4) {
                for (double y = 0; y <= 2.5; y += 0.4) {
                    player.spawnParticle(Particle.DUST, loc.clone().add(x, y, 0), 1, 0, 0, 0, 0, dust);
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("items.elytra.enabled", true)) return;
        if (event.isGliding()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfig().getString("items.elytra.message",
                "&cYou cannot use an Elytra while in combat!").replace("&", "§"));
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("items.trident.enabled", true)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.getConfig().getString("items.trident.message",
            "&cYou cannot use a Trident while in combat!").replace("&", "§"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        switch (item.getType()) {
            case ENDER_PEARL -> {
                if (plugin.getCooldownManager().isOnCooldown("ender-pearl", player.getUniqueId())) {
                    String msg = plugin.getConfig().getString("cooldowns.ender-pearl.message", "&cOn cooldown!")
                        .replace("{time}", String.valueOf(plugin.getCooldownManager().getRemainingSeconds("ender-pearl", player.getUniqueId())))
                        .replace("&", "§");
                    player.sendMessage(msg);
                    event.setCancelled(true);
                } else if (plugin.getConfig().getBoolean("cooldowns.ender-pearl.enabled", true)) {
                    plugin.getCooldownManager().setCooldown("ender-pearl", player.getUniqueId());
                    int seconds = plugin.getConfig().getInt("cooldowns.ender-pearl.seconds", 15);
                    player.setCooldown(Material.ENDER_PEARL, seconds * 20);
                }
            }
            case TRIDENT -> {
                if (!plugin.getConfig().getBoolean("items.trident.enabled", true)) break;
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasEnchant(org.bukkit.enchantments.Enchantment.RIPTIDE)) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfig().getString("items.trident.message",
                        "&cYou cannot use a Trident while in combat!").replace("&", "§"));
                }
            }
        }
    }

    @EventHandler
    public void onTotemUse(org.bukkit.event.entity.EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getCombatManager().isInCombat(player.getUniqueId())) return;
        if (plugin.getCooldownManager().isOnCooldown("totem", player.getUniqueId())) {
            String msg = plugin.getConfig().getString("cooldowns.totem.message", "&cTotem on cooldown!")
                .replace("{time}", String.valueOf(plugin.getCooldownManager().getRemainingSeconds("totem", player.getUniqueId())))
                .replace("&", "§");
            player.sendMessage(msg);
            event.setCancelled(true);
        } else if (plugin.getConfig().getBoolean("cooldowns.totem.enabled", true)) {
            plugin.getCooldownManager().setCooldown("totem", player.getUniqueId());
            int seconds = plugin.getConfig().getInt("cooldowns.totem.seconds", 10);
            player.setCooldown(Material.TOTEM_OF_UNDYING, seconds * 20);
        }
    }

    private boolean areTeammates(Player a, Player b) {
        // TeamsPlugin check
        try {
            org.bukkit.plugin.Plugin teamsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("TeamsPlugin");
            if (teamsPlugin == null) teamsPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("KoalaTeams");
            if (teamsPlugin != null) {
                Object teamManager = teamsPlugin.getClass().getMethod("getTeamManager").invoke(teamsPlugin);
                Object teamA = teamManager.getClass().getMethod("getPlayerTeam", java.util.UUID.class).invoke(teamManager, a.getUniqueId());
                Object teamB = teamManager.getClass().getMethod("getPlayerTeam", java.util.UUID.class).invoke(teamManager, b.getUniqueId());
                if (teamA != null && teamB != null) {
                    if (teamA == teamB) return true;
                    try {
                        Boolean allied = (Boolean) teamManager.getClass()
                            .getMethod("areAllies", teamA.getClass().getSuperclass(), teamB.getClass().getSuperclass())
                            .invoke(teamManager, teamA, teamB);
                        if (allied != null && allied) return true;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        // Towny check — same town or nation with friendly fire off
        try {
            org.bukkit.plugin.Plugin towny = org.bukkit.Bukkit.getPluginManager().getPlugin("Towny");
            if (towny != null) {
                Class<?> townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                Object townyAPI = townyAPIClass.getMethod("getInstance").invoke(null);

                Object residentA = townyAPIClass.getMethod("getResident", java.util.UUID.class).invoke(townyAPI, a.getUniqueId());
                Object residentB = townyAPIClass.getMethod("getResident", java.util.UUID.class).invoke(townyAPI, b.getUniqueId());
                if (residentA == null || residentB == null) return false;

                // Check same town
                Object townA = residentA.getClass().getMethod("getTownOrNull").invoke(residentA);
                Object townB = residentB.getClass().getMethod("getTownOrNull").invoke(residentB);
                if (townA != null && townB != null && townA.equals(townB)) {
                    // Check if friendly fire is off in this town
                    try {
                        boolean pvp = (boolean) townA.getClass().getMethod("isPVP").invoke(townA);
                        if (!pvp) return true;
                    } catch (Exception e) {
                        return true; // default to no combat if we can't check
                    }
                }

                // Check same nation
                try {
                    Object nationA = residentA.getClass().getMethod("getNationOrNull").invoke(residentA);
                    Object nationB = residentB.getClass().getMethod("getNationOrNull").invoke(residentB);
                    if (nationA != null && nationB != null && nationA.equals(nationB)) {
                        boolean pvp = (boolean) nationA.getClass().getMethod("isPVP").invoke(nationA);
                        if (!pvp) return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return false;
    }
}
