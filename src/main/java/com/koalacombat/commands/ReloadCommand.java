package com.koalacombat.commands;

import com.koalacombat.KoalaCombat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final KoalaCombat plugin;

    public ReloadCommand(KoalaCombat plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("koalacombat.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§cUsage: §f/koalacombat reload"); return true;
        }
        plugin.reloadConfig();
        sender.sendMessage("§aKoalaCombat config reloaded!");
        return true;
    }
}
