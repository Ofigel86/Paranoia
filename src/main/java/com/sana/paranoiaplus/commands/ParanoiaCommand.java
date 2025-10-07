package com.sana.paranoiaplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.sana.paranoiaplus.ParanoiaPlus;

public class ParanoiaCommand implements CommandExecutor {
    private final ParanoiaPlus plugin;
    public ParanoiaCommand(ParanoiaPlus plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("ParanoiaPlus v" + plugin.getDescription().getVersion());
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("toggle")) {
            // simple global toggle in config (in-memory)
            boolean enabled = plugin.getConfig().getBoolean("global.enabled", true);
            plugin.getConfig().set("global.enabled", !enabled);
            plugin.saveConfig();
            sender.sendMessage("ParanoiaPlus global enabled=" + !enabled);
            return true;
        } else if (sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("ParanoiaPlus config reloaded.");
            return true;
        }
        return false;
    }
}
