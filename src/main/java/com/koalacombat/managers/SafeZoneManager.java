package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class SafeZoneManager {

    private final KoalaCombat plugin;
    private boolean worldGuardEnabled = false;

    public SafeZoneManager(KoalaCombat plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardEnabled = true;
            plugin.getLogger().info("WorldGuard hooked!");
        }
    }

    public boolean isSafeZone(Location location) {
        if (!plugin.getConfig().getBoolean("safezones.enabled", true)) return false;

        // Check WorldGuard PVP flag first (most reliable)
        if (worldGuardEnabled && isWorldGuardNoPvp(location)) return true;

        // Fallback: check named regions from config
        if (worldGuardEnabled) return isInNamedRegion(location);

        return false;
    }

    private boolean isWorldGuardNoPvp(Location location) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return false;

            com.sk89q.worldedit.math.BlockVector3 pos = BukkitAdapter.asBlockVector(location);
            ApplicableRegionSet regions = regionManager.getApplicableRegions(pos);

            // Check if PVP flag is explicitly set to DENY in any applicable region
            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            if (pvpState == StateFlag.State.DENY) return true;

            // Also check BUILD flag (if build is denied, it's a protected/safe zone)
            StateFlag.State buildState = regions.queryState(null, Flags.BUILD);
            if (buildState == StateFlag.State.DENY) return true;

        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean isInNamedRegion(Location location) {
        java.util.List<String> safeRegions = plugin.getConfig().getStringList("safezones.regions");
        if (safeRegions.isEmpty()) return false;
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return false;
            com.sk89q.worldedit.math.BlockVector3 pos = BukkitAdapter.asBlockVector(location);
            for (var region : regionManager.getApplicableRegions(pos)) {
                if (safeRegions.contains(region.getId())) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
