package com.sana.paranoiaplus.modules;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.UUID;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.wrappers.EnumWrappers;

/**
 * ShadowModule - ProtocolLib-based implementation to spawn client-only "shadow" players.
 * Simplified to avoid direct compile-time dependency on authlib classes.
 */
public class ShadowModule {
    private final JavaPlugin plugin;
    private final CoreModule core;
    private ProtocolManager protocolManager;
    private final Map<UUID, Long> lastSpawn = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> spawnedEntityId = new ConcurrentHashMap<>(); // map target player -> entity id for cleanup
    private final Random random = new Random();

    public ShadowModule(JavaPlugin plugin, CoreModule core) {
        this.plugin = plugin;
        this.core = core;
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
        } catch (NoClassDefFoundError | Exception ex) {
            this.protocolManager = null;
            plugin.getLogger().warning("ProtocolLib not found — ShadowModule will be limited.");
        }
    }

    public void onEnable() {
        plugin.getLogger().info("ShadowModule enabled (ProtocolLib-aware).");
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!plugin.getConfig().getBoolean("shadow.enabled", true)) return;
                    if (!core.isServerHealthy()) return;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        trySpawnShadowCheck(p);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Shadow periodic task error: " + t.getMessage());
                }
            }
        }.runTaskTimer(plugin, 20L * 15, 20L * 15); // every 15s check
    }

    public void onDisable() {
        plugin.getLogger().info("ShadowModule disabled.");
        // cleanup if needed
    }

    private void trySpawnShadowCheck(Player p) {
        if (p.getGameMode().name().equalsIgnoreCase("CREATIVE")) return;
        if (p.getLocation().getBlock().getLightLevel() > plugin.getConfig().getInt("shadow.min-light-level", 7)) return;
        long last = lastSpawn.getOrDefault(p.getUniqueId(), 0L);
        long cooldown = getRandomCooldownMs();
        long now = System.currentTimeMillis();
        if (now - last < cooldown) return;
        double aloneRadius = plugin.getConfig().getDouble("shadow.require-alone-radius", 40.0);
        boolean someoneNearby = Bukkit.getOnlinePlayers().stream()
                .anyMatch(o -> !o.getUniqueId().equals(p.getUniqueId()) &&
                        o.getLocation().distanceSquared(p.getLocation()) < aloneRadius * aloneRadius);
        if (someoneNearby) return;
        Location spawn = computeSpawnAwayFromPlayer(p, plugin.getConfig().getInt("shadow.distance-min", 20), plugin.getConfig().getInt("shadow.distance-max", 40));
        if (spawn == null) return;
        spawnShadowFor(p, spawn);
        lastSpawn.put(p.getUniqueId(), now);
    }

    private long getRandomCooldownMs() {
        List<Integer> arr = plugin.getConfig().getIntegerList("shadow.cooldown-seconds");
        if (arr == null || arr.isEmpty()) return 300_000L;
        int s = arr.get(random.nextInt(arr.size()));
        return s * 1000L;
    }

    private Location computeSpawnAwayFromPlayer(Player p, int min, int max) {
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        World w = p.getWorld();
        for (int attempts = 0; attempts < 12; attempts++) {
            double dist = min + random.nextDouble() * (max - min);
            double angle = Math.toRadians((plugin.getConfig().getInt("shadow.fov-deg", 30) + plugin.getConfig().getInt("shadow.fov-margin-deg", 8)) + (5 + random.nextDouble() * 120));
            double yaw = Math.atan2(look.getZ(), look.getX()) + angle * (random.nextBoolean() ? 1 : -1);
            double dx = Math.cos(yaw) * dist;
            double dz = Math.sin(yaw) * dist;
            Location candidate = eye.clone().add(dx, 0, dz);
            candidate.setY(Math.max(2, Math.min(candidate.getY(), plugin.getConfig().getInt("shadow.max-y", 60))));
            if (candidate.getBlock().getLightLevel() <= plugin.getConfig().getInt("shadow.min-light-level", 7))
                return candidate;
        }
        return null;
    }

    // Public API: spawn a shadow at a specific world location for a target player
    public void spawnShadowFor(Player target, Location spawnLoc) {
        if (protocolManager == null) {
            plugin.getLogger().info("ProtocolLib missing — fallback shadow log for " + target.getName());
            lastSpawn.put(target.getUniqueId(), System.currentTimeMillis());
            return;
        }
        UUID fakeUuid = UUID.randomUUID();
        int entityId = random.nextInt(Integer.MAX_VALUE / 2) + 1000;
        spawnedEntityId.put(target.getUniqueId(), entityId);

        try {
            // Create a simple WrappedGameProfile (avoid direct authlib usage)
            WrappedGameProfile profile = new WrappedGameProfile(fakeUuid, " ");

            // NAMED_ENTITY_SPAWN (player-like entity)
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            spawn.getIntegers().write(0, entityId);
            spawn.getUUIDs().write(0, fakeUuid);
            spawn.getDoubles().write(0, spawnLoc.getX());
            spawn.getDoubles().write(1, spawnLoc.getY());
            spawn.getDoubles().write(2, spawnLoc.getZ());
            spawn.getBytes().write(0, (byte) 0);
            spawn.getBytes().write(1, (byte) 0);
            protocolManager.sendServerPacket(target, spawn);

            // ENTITY_METADATA: minimal watcher
            PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            meta.getIntegers().write(0, entityId);
            meta.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            protocolManager.sendServerPacket(target, meta);

            // HEAD_ROTATION
            PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, entityId);
            head.getBytes().write(0, (byte) 0);
            protocolManager.sendServerPacket(target, head);

            // Schedule destroy for retreat effect
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                        destroy.getIntegerArrays().write(0, new int[]{entityId});
                        protocolManager.sendServerPacket(target, destroy);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to destroy shadow entity: " + ex.getMessage());
                    }
                }
            }.runTaskLater(plugin, 20L * (1 + random.nextInt(3)));
        } catch (Exception ex) {
            plugin.getLogger().warning("ProtocolLib send error: " + ex.getMessage());
        }
    }

    public void spawnShadowFor(Player p) {
        Location spawn = computeSpawnAwayFromPlayer(p, plugin.getConfig().getInt("shadow.distance-min", 20), plugin.getConfig().getInt("shadow.distance-max", 40));
        if (spawn != null) spawnShadowFor(p, spawn);
    }

    public void revealShadowFor(Player player) {
        Integer id = spawnedEntityId.remove(player.getUniqueId());
        if (id != null && protocolManager != null) {
            try {
                PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroy.getIntegerArrays().write(0, new int[]{id});
                protocolManager.sendServerPacket(player, destroy);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to destroy shadow during reveal: " + ex.getMessage());
            }
        }
    }
}
