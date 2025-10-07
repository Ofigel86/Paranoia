package com.sana.paranoiaplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;
import com.sana.paranoiaplus.ParanoiaPlus;
import com.sana.paranoiaplus.modules.ShadowModule;

public class ShadowCommand implements CommandExecutor {
    private final ParanoiaPlus plugin;
    private final ShadowModule shadow;

    public ShadowCommand(ParanoiaPlus plugin, ShadowModule shadow) {
        this.plugin = plugin;
        this.shadow = shadow;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase();
        try {
            if (sub.equals("start") || sub.equals("once")) {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /shadow start|once <player>"); return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found"); return true; }
                shadow.spawnShadowFor(target);
                sender.sendMessage("Shadow spawn requested for " + target.getName());
                return true;
            } else if (sub.equals("stop")) {
                if (args.length < 2) { sender.sendMessage("Usage: /shadow stop <player>"); return true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found"); return true; }
                shadow.revealShadowFor(target);
                sender.sendMessage("Shadow removed for " + target.getName());
                return true;
            } else if (sub.equals("reveal")) {
                if (args.length < 2) { sender.sendMessage("Usage: /shadow reveal <player>"); return true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found"); return true; }
                shadow.revealShadowFor(target);
                sender.sendMessage("Shadow revealed for " + target.getName());
                return true;
            } else if (sub.equals("reload")) {
                plugin.reloadConfig();
                sender.sendMessage("ParanoiaPlus config reloaded.");
                return true;
            }
        } catch (Exception ex) {
            sender.sendMessage("Command error: " + String.valueOf(ex.getMessage()));
            throw new CommandException(String.valueOf(ex.getMessage()));
        }
        return false;
    }
}
