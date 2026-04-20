package com.koalacombat.managers;

import com.koalacombat.KoalaCombat;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

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
        if (!worldGuardEnabled) return false;

        List<String> safeRegions = plugin.getConfig().getStringList("safezones.regions");
        if (safeRegions.isEmpty()) return false;

        try {
            RegionManager regionManager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) return false;

            com.sk89q.worldedit.math.BlockVector3 pos = BukkitAdapter.asBlockVector(location);
            for (ProtectedRegion region : regionManager.getApplicableRegions(pos)) {
                if (safeRegions.contains(region.getId())) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
