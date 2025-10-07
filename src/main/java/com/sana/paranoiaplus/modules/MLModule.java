package com.sana.paranoiaplus.modules;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.EventPriority;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.util.logging.Level;
import java.io.*;

/**
 * MLModule - lightweight in-plugin collector + simple heuristic whitelist model with persistence and EMA smoothing.
 */
public class MLModule implements Listener {
    private final JavaPlugin plugin;
    private final CoreModule core;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final ConcurrentHashMap<Material, Double> emaCounts = new ConcurrentHashMap<>();
    private volatile List<Material> allowed = Collections.emptyList();
    private final Gson gson = new Gson();
    private final File persistFile;

    public MLModule(JavaPlugin plugin, CoreModule core) {
        this.plugin = plugin;
        this.core = core;
        this.persistFile = new File(plugin.getDataFolder(), "ml_counts.json");
    }

    public void onEnable() {
        enabled.set(true);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadPersisted();
        plugin.getLogger().info("MLModule enabled (collector + EMA + persistence).");
        long seconds = plugin.getConfig().getLong("ml.whitelist.update-interval-seconds", 300L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled.get()) return;
                if (!core.isServerHealthy()) return;
                recomputeWhitelist();
                persistCounts();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * seconds, 20L * seconds);
    }

    public void onDisable() {
        enabled.set(false);
        persistCounts();
        plugin.getLogger().info("MLModule disabled.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent ev) {
        if (!enabled.get()) return;
        if (ev.getPlayer().getGameMode().name().equalsIgnoreCase("CREATIVE") && plugin.getConfig().getBoolean("ml.whitelist.exclude-creative", true)) return;
        addSample(ev.getBlockPlaced().getType(), 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent ev) {
        if (!enabled.get()) return;
        if (ev.getPlayer().getGameMode().name().equalsIgnoreCase("CREATIVE") && plugin.getConfig().getBoolean("ml.whitelist.exclude-creative", true)) return;
        addSample(ev.getBlock().getType(), 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent ev) {
        if (!enabled.get()) return;
        addSample(ev.getItem().getItemStack().getType(), 0.5);
    }

    private void addSample(Material m, double weight) {
        emaCounts.merge(m, weight, (old, v) -> old * (1.0 - getAlpha()) + v * getAlpha());
    }

    private double getAlpha() {
        return plugin.getConfig().getDouble("ml.whitelist.ema-alpha", 0.3);
    }

    private void recomputeWhitelist() {
        int topK = plugin.getConfig().getInt("ml.whitelist.top-k", 20);
        Set<Material> hardDeny = loadHardDeny();
        List<Map.Entry<Material, Double>> list = new ArrayList<>(emaCounts.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Material> newAllowed = new ArrayList<>();
        for (Map.Entry<Material, Double> e : list) {
            if (newAllowed.size() >= topK) break;
            if (hardDeny.contains(e.getKey())) continue;
            newAllowed.add(e.getKey());
        }
        // ensure seeds are present
        List<Material> seeds = loadSeedAllow();
        for (Material m : seeds) if (!newAllowed.contains(m)) newAllowed.add(m);
        allowed = Collections.unmodifiableList(newAllowed);
        plugin.getLogger().info("MLModule recomputed allowed list: size=" + allowed.size());
    }

    private Set<Material> loadHardDeny() {
        Set<Material> deny = EnumSet.noneOf(Material.class);
        List<String> names = plugin.getConfig().getStringList("ml.whitelist.hard-deny.materials");
        for (String s : names) {
            try { deny.add(Material.valueOf(s.toUpperCase())); } catch (Exception ignored) {}
        }
        return deny;
    }

    private List<Material> loadSeedAllow() {
        List<Material> out = new ArrayList<>();
        List<String> names = plugin.getConfig().getStringList("ml.whitelist.seed-allow");
        for (String s : names) {
            try { out.add(Material.valueOf(s.toUpperCase())); } catch (Exception ignored) {}
        }
        return out;
    }

    // Persistence: save emaCounts to JSON
    private void persistCounts() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            Map<String, Double> map = new HashMap<>();
            for (Map.Entry<Material, Double> e : emaCounts.entrySet()) map.put(e.getKey().name(), e.getValue());
            try (Writer w = new FileWriter(persistFile)) {
                gson.toJson(map, w);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to persist ML counts: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPersisted() {
        try {
            if (!persistFile.exists()) return;
            try (Reader r = new FileReader(persistFile)) {
                Map<String, Double> map = gson.fromJson(r, new TypeToken<Map<String, Double>>(){}.getType());
                if (map == null) return;
                for (Map.Entry<String, Double> e : map.entrySet()) {
                    try { Material m = Material.valueOf(e.getKey()); emaCounts.put(m, e.getValue()); } catch (Exception ignored) {}
                }
                plugin.getLogger().info("MLModule loaded persisted counts: " + emaCounts.size());
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load persisted ML counts: " + ex.getMessage());
        }
    }

    // Public API
    public boolean isAllowed(Material m) {
        return allowed.contains(m);
    }

    public List<Material> getAllowedSnapshot() {
        return allowed;
    }

    public Map<Material, Double> getTopMap(int n) {
        List<Map.Entry<Material, Double>> list = new ArrayList<>(emaCounts.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<Material, Double> out = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<Material, Double> e : list) {
            if (i++ >= n) break;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
