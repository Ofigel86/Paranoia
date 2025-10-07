package com.sana.paranoiaplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.sana.paranoiaplus.ParanoiaPlus;
import com.sana.paranoiaplus.modules.FakeModule;

public class FakeCommand implements CommandExecutor {
    private final ParanoiaPlus plugin;
    private final FakeModule fake;

    public FakeCommand(ParanoiaPlus plugin, FakeModule fake) {
        this.plugin = plugin;
        this.fake = fake;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase();
        try {
            if (sub.equals("spawn")) {
                if (args.length < 3) { sender.sendMessage("Usage: /fake spawn <name> <player>"); return true; }
                String name = args[1];
                Player target = plugin.getServer().getPlayer(args[2]);
                if (target == null) { sender.sendMessage("Player not found"); return true; }
                boolean ok = fake.spawnBot(name, target);
                sender.sendMessage(ok ? "Bot spawned" : "Failed to spawn bot (limit?)");
                return true;
            } else if (sub.equals("remove")) {
                if (args.length < 2) { sender.sendMessage("Usage: /fake remove <name>"); return true; }
                fake.removeBot(args[1]);
                sender.sendMessage("Bot removed (if existed)"); return true;
            } else if (sub.equals("info")) {
                sender.sendMessage("Fake module: bots=" + fake.getBotCount());
                return true;
            }
        } catch (Exception ex) {
            sender.sendMessage("Command error: " + ex.getMessage());
        }
        return false;
    }
}
