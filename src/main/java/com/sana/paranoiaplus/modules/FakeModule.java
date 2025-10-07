package com.sana.paranoiaplus.modules;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FakeModule - improved with real placement check (uses MLModule whitelist) and rate-limiting.
 */
public class FakeModule {
    private final JavaPlugin plugin;
    private final CoreModule core;
    private final Map<String, Bot> bots = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPlace = new ConcurrentHashMap<>(); // per-bot last placement time ms

    public FakeModule(JavaPlugin plugin, CoreModule core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void onEnable() {
        plugin.getLogger().info("FakeModule enabled (improved with safety checks).");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("fake.enabled", true)) return;
                if (!core.isServerHealthy()) return;
                for (Bot b : bots.values()) {
                    try { b.tick(); } catch (Throwable t) { plugin.getLogger().warning("Bot tick error: " + t.getMessage()); }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void onDisable() {
        for (Bot b : bots.values()) b.kill();
        bots.clear();
        plugin.getLogger().info("FakeModule disabled.");
    }

    // Safely place a block if allowed by ML-module and config rate limits.
    // Returns true if placed successfully, false otherwise.
    public boolean placeBlockReal(String botName, Location loc, Material m) {
        MLModule ml = getMl();
        if (ml == null) return false;
        if (!ml.isAllowed(m)) {
            plugin.getLogger().info("ML denied placement of " + m.name() + " by bot " + botName);
            return false;
        }
        // rate-limit per bot
        long now = System.currentTimeMillis();
        long last = lastPlace.getOrDefault(botName, 0L);
        long minInterval = plugin.getConfig().getLong("fake.build.real-place-interval-ms", 1000L);
        if (now - last < minInterval) return false;

        // safety: disallow hard-deny materials
        List<String> hard = plugin.getConfig().getStringList("ml.whitelist.hard-deny.materials");
        if (hard != null && hard.contains(m.name())) {
            plugin.getLogger().info("Hard-deny material for real placement: " + m.name());
            return false;
        }

        // try to find bot actor to fire BlockPlaceEvent with a Player
        Bot bot = bots.get(botName);
        Player actor = (bot != null) ? bot.getTarget() : null;

        Block b = loc.getBlock();

        if (actor != null) {
            // Use event so other plugins may cancel it
            BlockPlaceEvent ev = new BlockPlaceEvent(b, b.getState(), b, actor.getInventory().getItemInMainHand(), actor, true);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return false;
            b.setType(m, true);
        } else {
            // No actor available — either reject or place silently. We place silently to not throw.
            b.setType(m, true);
        }

        lastPlace.put(botName, now);
        plugin.getLogger().info("Bot " + botName + " placed " + m.name() + " at " + loc.toString());
        return true;
    }

    private MLModule getMl() {
        if (!(plugin instanceof com.sana.paranoiaplus.ParanoiaPlus)) return null;
        return ((com.sana.paranoiaplus.ParanoiaPlus) plugin).getMlModule();
    }

    public boolean spawnBot(String name, Player target) {
        if (bots.size() >= plugin.getConfig().getInt("fake.max-bots", 1)) return false;
        Bot b = new Bot(name, target);
        bots.put(name, b);
        plugin.getLogger().log(Level.INFO, "Spawned bot {0} for target {1}", new Object[]{name, target.getName()});
        return true;
    }

    public void removeBot(String name) {
        Bot b = bots.remove(name);
        if (b != null) b.kill();
    }

    public int getBotCount() { return bots.size(); }

    private static enum State { PEEK, RETREAT, GATHER, BUILD, VANISH }

    private class Bot {
        private final String name;
        private final Player target;
        private long lastAction = 0L;
        private State state = State.PEEK;

        public Bot(String name, Player target) {
            this.name = name;
            this.target = target;
        }

        public Player getTarget() { return target; }

        public void tick() {
            long now = System.currentTimeMillis();
            if (now - lastAction < 1000) return;
            lastAction = now;
            switch (state) {
                case PEEK:
                    state = State.RETREAT;
                    break;
                case RETREAT:
                    state = State.GATHER;
                    break;
                case GATHER:
                    // try to place a simple block near target as 'build' demo
                    Location loc = target.getLocation().add(2, 0, 2);
                    placeBlockReal(name, loc, Material.OAK_PLANKS);
                    state = State.BUILD;
                    break;
                case BUILD:
                    state = State.VANISH;
                    break;
                case VANISH:
                    kill();
                    break;
            }
        }

        public void kill() {
            plugin.getLogger().fine("Bot " + name + " killed");
        }
    }
}
