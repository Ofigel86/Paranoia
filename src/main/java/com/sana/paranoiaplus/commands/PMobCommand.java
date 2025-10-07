package com.sana.paranoiaplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.sana.paranoiaplus.ParanoiaPlus;
import com.sana.paranoiaplus.modules.MobsModule;
import org.bukkit.entity.EntityType;

public class PMobCommand implements CommandExecutor {
    private final ParanoiaPlus plugin;
    private final MobsModule mobs;

    public PMobCommand(ParanoiaPlus plugin, MobsModule mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase();
        if (sub.equals("spawn")) {
            if (!(sender instanceof Player)) { sender.sendMessage("This command must be run by a player."); return true; }
            Player p = (Player) sender;
            if (args.length < 2) { sender.sendMessage("Usage: /pmob spawn <zombie|skeleton|...>"); return true; }
            try {
                EntityType t = EntityType.valueOf(args[1].toUpperCase());
                mobs.spawnControlled(t, p);
                sender.sendMessage("Spawn requested: " + t.name());
            } catch (Exception ex) {
                sender.sendMessage("Unknown entity type."); 
            }
            return true;
        } else if (sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("ParanoiaPlus config reloaded.");
            return true;
        }
        return false;
    }
}
