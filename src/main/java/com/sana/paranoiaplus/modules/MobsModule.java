package com.sana.paranoiaplus.modules;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import java.util.Random;
import java.util.logging.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MobsModule - improved spawn replacement skeleton and per-entity freeze-on-look controller.
 */
public class MobsModule implements Listener {
    private final JavaPlugin plugin;
    private final CoreModule core;
    private final Set<UUID> controlled = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    public MobsModule(JavaPlugin plugin, CoreModule core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void onEnable() {
        plugin.getLogger().info("MobsModule enabled (improved).");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Schedule controller tick
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("mobs.replace-natural-spawns", true)) return;
                if (!core.isServerHealthy()) return;
                // iterate controlled entities and ensure controllers applied (placeholder)
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 5, 20L * 5);
    }

    public void onDisable() {
        plugin.getLogger().info("MobsModule disabled.");
    }

    // Example API for spawning a controlled paranoia mob (returns the entity if applicable)
    public LivingEntity spawnControlled(EntityType type, Player nearPlayer) {
        plugin.getLogger().log(Level.INFO, "(mobs) spawn request {0} near {1}", new Object[]{type.name(), nearPlayer.getName()});
        // spawn at player's location offset
        Location loc = nearPlayer.getLocation().add(nearPlayer.getLocation().getDirection().multiply(6.0));
        LivingEntity e = (LivingEntity) nearPlayer.getWorld().spawnEntity(loc, type);
        if (e instanceof Mob) {
            controlled.add(e.getUniqueId());
        }
        return e;
    }

    // Freeze-on-look check: if any player looks at controlled entity, disable its AI
    private boolean isPlayerLookingAt(Player player, LivingEntity entity, double fovDeg) {
        Location eye = player.getEyeLocation();
        Location target = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        org.bukkit.util.Vector toTarget = target.toVector().subtract(eye.toVector()).normalize();
        double dot = dir.dot(toTarget);
        double cos = Math.cos(Math.toRadians(fovDeg));
        if (dot < cos) return false;
        // raytrace check for line of sight
        org.bukkit.util.RayTraceResult r = player.getWorld().rayTrace(eye, toTarget, eye.distance(target), org.bukkit.FluidCollisionMode.NEVER, true, 0.1, e -> e.equals(entity));
        return r != null && r.getHitEntity() != null && r.getHitEntity().getUniqueId().equals(entity.getUniqueId());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onEntitySpawn(EntitySpawnEvent ev) {
        // if replacement rules apply, mark for control
        if (!(ev.getEntity() instanceof LivingEntity)) return;
        LivingEntity le = (LivingEntity) ev.getEntity();
        // Example rule: only replace some spawns probabilistically
        if (new Random().nextDouble() < plugin.getConfig().getDouble("mobs.replace-chance", 0.25)) {
            controlled.add(le.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent ev) {
        controlled.remove(ev.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent ev) {
        // cleanup potentially related data
    }
}
