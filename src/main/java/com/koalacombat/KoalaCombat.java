package com.koalacombat;

import com.koalacombat.commands.ReloadCommand;
import com.koalacombat.listeners.CombatListener;
import com.koalacombat.managers.CombatManager;
import com.koalacombat.managers.CooldownManager;
import com.koalacombat.managers.SafeZoneManager;
import org.bukkit.plugin.java.JavaPlugin;

public class KoalaCombat extends JavaPlugin {

    private static KoalaCombat instance;
    private CombatManager combatManager;
    private CooldownManager cooldownManager;
    private SafeZoneManager safeZoneManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        combatManager = new CombatManager(this);
        cooldownManager = new CooldownManager(this);
        safeZoneManager = new SafeZoneManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getCommand("koalacombat").setExecutor(new ReloadCommand(this));

        getLogger().info("KoalaCombat enabled!");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) combatManager.cleanup();
        getLogger().info("KoalaCombat disabled.");
    }

    public static KoalaCombat getInstance() { return instance; }
    public CombatManager getCombatManager() { return combatManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public SafeZoneManager getSafeZoneManager() { return safeZoneManager; }
}
