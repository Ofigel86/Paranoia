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
import java.lang.reflect.Field;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.comphenix.protocol.wrappers.EnumWrappers;

import com.mojang.authlib.properties.Property;

/**
 * ShadowModule - ProtocolLib-based implementation to spawn client-only "shadow" players.
 * This is a careful skeleton implementing the recommended packet flow:
 * 1) PLAYER_INFO (ADD) with GameProfile
 * 2) NAMED_ENTITY_SPAWN (with entity id, uuid, x,y,z, yaw,pitch)
 * 3) ENTITY_METADATA / HEAD_ROTATION
 * 4) optionally remove from player list (PLAYER_INFO remove)
 * 5) schedule ENTITY_DESTROY when hiding
 *
 * Notes: This code assumes ProtocolLib is present. If not, the module logs a warning and remains inert.
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
        // schedule periodic checks to try spawning shadows
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
        // remove any spawned client-only entities (best-effort)
        for (UUID u : spawnedEntityId.keySet()) {
            // cannot easily find player by UUID for cleanup target; this is placeholder
        }
    }

    private void trySpawnShadowCheck(Player p) {
        if (p.getGameMode().name().equalsIgnoreCase("CREATIVE")) return;
        if (p.getLocation().getBlock().getLightLevel() > plugin.getConfig().getInt("shadow.min-light-level", 7)) return;
        long last = lastSpawn.getOrDefault(p.getUniqueId(), 0L);
        long cooldown = getRandomCooldownMs();
        long now = System.currentTimeMillis();
        if (now - last < cooldown) return;
        // ensure alone
        double aloneRadius = plugin.getConfig().getDouble("shadow.require-alone-radius", 40.0);
        boolean someoneNearby = Bukkit.getOnlinePlayers().stream().anyMatch(o -> !o.getUniqueId().equals(p.getUniqueId()) && o.getLocation().distanceSquared(p.getLocation()) < aloneRadius * aloneRadius);
        if (someoneNearby) return;
        // compute spawn pos 20-40 blocks away outside FOV direction
        Location spawn = computeSpawnAwayFromPlayer(p, plugin.getConfig().getInt("shadow.distance-min", 20), plugin.getConfig().getInt("shadow.distance-max", 40));
        if (spawn == null) return;
        // spawn shadow via ProtocolLib
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
        for (int attempts=0; attempts<12; attempts++) {
            double dist = min + random.nextDouble() * (max - min);
            // pick a random angle outside FOV margin
            double angle = Math.toRadians( (plugin.getConfig().getInt("shadow.fov-deg", 30) + plugin.getConfig().getInt("shadow.fov-margin-deg", 8)) + (5 + random.nextDouble()*120) );
            double yaw = Math.atan2(look.getZ(), look.getX()) + angle * (random.nextBoolean() ? 1 : -1);
            double dx = Math.cos(yaw) * dist;
            double dz = Math.sin(yaw) * dist;
            Location candidate = eye.clone().add(dx, 0, dz);
            candidate.setY(Math.max(2, Math.min(candidate.getY(), plugin.getConfig().getInt("shadow.max-y", 60))));
            if (candidate.getBlock().getLightLevel() <= plugin.getConfig().getInt("shadow.min-light-level", 7)) return candidate;
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
        // create a unique fake UUID for the shadow instance for this spawn
        UUID fakeUuid = UUID.randomUUID();
        int entityId = random.nextInt(Integer.MAX_VALUE/2) + 1000;
        spawnedEntityId.put(target.getUniqueId(), entityId);

        // Build PLAYER_INFO ADD packet with GameProfile (wrapped)
        try {
            // Build a basic GameProfile via WrappedGameProfile; using no skin texture by default
            WrappedGameProfile profile = WrappedGameProfile.fromHandle(com.mojang.authlib.GameProfile.class.getConstructor(UUID.class, String.class).newInstance(fakeUuid, " "));
            // You can optionally attach a black skin texture property here if value/signature are available in config
            PacketContainer addPlayer = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
            addPlayer.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            // write a single PlayerInfoData entry list
            com.comphenix.protocol.wrappers.WrappedPlayerInfoData infoData = new com.comphenix.protocol.wrappers.WrappedPlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.NOT_SET, WrappedChatComponent.fromText(""));
            addPlayer.getPlayerInfoDataLists().write(0, Arrays.asList(infoData));
            protocolManager.sendServerPacket(target, addPlayer);

            // NAMED_ENTITY_SPAWN
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
            spawn.getIntegers().write(0, entityId);
            spawn.getUUIDs().write(0, fakeUuid);
            spawn.getDoubles().write(0, spawnLoc.getX());
            spawn.getDoubles().write(1, spawnLoc.getY());
            spawn.getDoubles().write(2, spawnLoc.getZ());
            spawn.getBytes().write(0, (byte)0); // yaw
            spawn.getBytes().write(1, (byte)0); // pitch
            protocolManager.sendServerPacket(target, spawn);

            // ENTITY_METADATA placeholder: send empty watcher to ensure proper skin/head rotation behaviour
            PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            meta.getIntegers().write(0, entityId);
            meta.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            protocolManager.sendServerPacket(target, meta);

            // HEAD_ROTATION
            PacketContainer head = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, entityId);
            head.getBytes().write(0, (byte)0);
            protocolManager.sendServerPacket(target, head);

            // Optionally remove from player list after a short delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PacketContainer remove = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
                        remove.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
                        remove.getPlayerInfoDataLists().write(0, Arrays.asList(infoData));
                        protocolManager.sendServerPacket(target, remove);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to remove player info for shadow: " + ex.getMessage());
                    }
                }
            }.runTaskLater(plugin, 20L * 1);

            // Schedule the destroy after random short interval for "retreat" effect
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
            }.runTaskLater(plugin, 20L * (1 + random.nextInt(3))); // 1-3 seconds visible
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Failed to build shadow packets reflectively: " + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().warning("ProtocolLib send error: " + ex.getMessage());
        }
    }

    // Overload for older code calling spawnShadowFor(Player p)
    public void spawnShadowFor(Player p) {
        Location spawn = computeSpawnAwayFromPlayer(p, plugin.getConfig().getInt("shadow.distance-min", 20), plugin.getConfig().getInt("shadow.distance-max", 40));
        if (spawn != null) spawnShadowFor(p, spawn);
    }

    public void revealShadowFor(Player player) {
        // Best-effort: send ENTITY_DESTROY if we tracked id
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
