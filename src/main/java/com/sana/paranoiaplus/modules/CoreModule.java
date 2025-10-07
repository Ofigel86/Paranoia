package com.sana.paranoiaplus.modules;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicDouble;
import java.util.logging.Level;

/**
 * CoreModule - improved: provides reliable config access, TPS guard and helper utilities.
 */
public class CoreModule {
    protected final JavaPlugin plugin;
    private final TpsMonitor tpsMonitor;

    public CoreModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tpsMonitor = new TpsMonitor();
    }

    public void onEnable() {
        plugin.getLogger().info("CoreModule enabled (improved).");
        // Start TPS monitor
        new BukkitRunnable() {
            @Override
            public void run() {
                tpsMonitor.sample();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L);
    }

    public void onDisable() {
        plugin.getLogger().info("CoreModule disabled.");
    }

    public double getRecentTps() {
        return tpsMonitor.getTps();
    }

    public boolean isServerHealthy() {
        return getRecentTps() >= plugin.getConfig().getDouble("global.min-tps", 18.0);
    }

    // Small inner helper for TPS monitoring (smoothed)
    private static class TpsMonitor {
        private final double[] samples = new double[10];
        private int idx = 0;
        private boolean filled = false;

        public void sample() {
            double tps = Bukkit.getServer().getTPS() != null ? Bukkit.getServer().getTPS()[0] : 20.0;
            samples[idx++] = tps;
            if (idx >= samples.length) { idx = 0; filled = true; }
        }

        public double getTps() {
            int len = filled ? samples.length : idx;
            if (len == 0) return 20.0;
            double sum = 0;
            for (int i = 0; i < len; i++) sum += samples[i];
            return sum / len;
        }
    }
}
