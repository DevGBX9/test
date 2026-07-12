package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Object serverPlayer;
    private Player bukkitPlayer;
    private boolean alive;

    // Cached reflection handles for tick
    private Method tickMethod;
    private Method doTickMethod;
    private Field connectionField;

    // Keepalive fields — cached once
    private Object cachedListener;
    private Object cachedRawConn;
    private Field keepAliveTimeField;
    private Field tickCountField;
    private Field lastReceivedTimeField;
    private boolean keepAliveCached = false;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public boolean isAlive() { return alive; }
    public Player getBukkitPlayer() { return bukkitPlayer; }
    public Object getServerPlayer() { return serverPlayer; }

    public void spawn(Location location, Object profile) {
        if (alive) return;
        if (!NMSHelper.isAvailable()) {
            Bukkit.getLogger().severe("[Mineflayer] NMS unavailable");
            return;
        }
        try {
            serverPlayer = NMSHelper.createAndJoinFakePlayer(name, location, profile);
            bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
            bukkitPlayer.teleport(location);

            // Force gravity ON and gamemode to SURVIVAL for proper physics
            bukkitPlayer.setGravity(true);
            bukkitPlayer.setGameMode(org.bukkit.GameMode.SURVIVAL);

            NMSHelper.registerEntityInWorld(serverPlayer, location.getWorld());
            NMSHelper.broadcastBotSpawn(serverPlayer);

            // Cache tick method from ServerPlayer or its superclasses
            for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                try {
                    tickMethod = cls.getDeclaredMethod("doTick");
                    tickMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (tickMethod == null) {
                for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                    try {
                        tickMethod = cls.getDeclaredMethod("tick");
                        tickMethod.setAccessible(true);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            // Cache connection field
            connectionField = null;
            try {
                Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                for (Field f : serverPlayer.getClass().getDeclaredFields()) {
                    if (f.getType().isAssignableFrom(listenerCls)) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
                // Also check superclasses
                if (connectionField == null) {
                    for (Class<?> cls = serverPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (listenerCls.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                connectionField = f;
                                break;
                            }
                        }
                        if (connectionField != null) break;
                    }
                }
            } catch (Exception ignored) {}

            broadcastJoin(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned (tickMethod=" + (tickMethod != null ? tickMethod.getName() : "null") + ", connectionField=" + (connectionField != null) + ")");
            alive = true;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] spawn failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void remove() {
        if (!alive) return;
        if (bukkitPlayer != null) {
            try { bukkitPlayer.kickPlayer("Bot removed"); } catch (Exception ignored) {}
        }
        if (serverPlayer != null) {
            try { NMSHelper.removeFakePlayer(serverPlayer); } catch (Exception ignored) {}
        }
        broadcastLeave(name);
        serverPlayer = null;
        bukkitPlayer = null;
        alive = false;
    }

    public void tick() {
        if (!alive || serverPlayer == null || bukkitPlayer == null) return;

        try {
            // === 1. KEEPALIVE: Prevent timeout disconnect ===
            ensureKeepalive();

            // === 2. NATIVE TICK: Call ServerPlayer.doTick() or tick() for full physics ===
            if (tickMethod != null) {
                try {
                    tickMethod.invoke(serverPlayer);
                } catch (Exception e) {
                    // Swallow NPEs from connection-related code inside tick()
                    // The tick still processes physics/gravity before hitting connection logic
                }
            } else {
                // Fallback: manual gravity if tick method unavailable
                manualPhysicsFallback();
            }

            // === 3. HEAD TRACKING: Look at nearest player ===
            if (bukkitPlayer.isOnline()) {
                Player nearest = findNearestPlayer();
                if (nearest != null) {
                    Location botEyeLoc = bukkitPlayer.getEyeLocation();
                    Location targetLoc = nearest.getEyeLocation();
                    double dx = targetLoc.getX() - botEyeLoc.getX();
                    double dy = targetLoc.getY() - botEyeLoc.getY();
                    double dz = targetLoc.getZ() - botEyeLoc.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 0.001 || Math.abs(dy) > 0.001) {
                        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        if (pitch > 90) pitch = 90;
                        if (pitch < -90) pitch = -90;
                        NMSHelper.setRotation(serverPlayer, yaw, pitch, yaw);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Fallback manual gravity simulation if ServerPlayer.tick() is unavailable.
     */
    private void manualPhysicsFallback() {
        try {
            org.bukkit.util.Vector velocity = bukkitPlayer.getVelocity();
            double vx = velocity.getX();
            double vy = velocity.getY();
            double vz = velocity.getZ();

            boolean onGround = bukkitPlayer.isOnGround();
            boolean hasGravity = bukkitPlayer.hasGravity();

            if (hasGravity && !onGround) {
                vy -= 0.08;
                if (vy < -3.92) vy = -3.92;
            }

            double friction = onGround ? 0.546 : 0.98;
            vx *= friction;
            vz *= friction;

            Object vec3Move = NMSHelper.createVec3(vx, vy, vz);
            if (vec3Move != null) {
                NMSHelper.move(serverPlayer, vec3Move);
            }

            org.bukkit.util.Vector postVel = bukkitPlayer.getVelocity();
            double pvx = postVel.getX();
            double pvy = postVel.getY();
            double pvz = postVel.getZ();
            if (bukkitPlayer.isOnGround() && pvy < 0) {
                pvy = 0;
            }
            bukkitPlayer.setVelocity(new org.bukkit.util.Vector(pvx, pvy, pvz));
        } catch (Exception ignored) {}
    }

    /**
     * Prevents the server from kicking the bot for timeout.
     * Resets keepalive timers on the connection and listener.
     */
    private void ensureKeepalive() {
        try {
            if (!keepAliveCached && connectionField != null) {
                keepAliveCached = true;
                cachedListener = connectionField.get(serverPlayer);
                if (cachedListener != null) {
                    // Find raw Connection object inside the listener
                    for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            if (f.getType().getName().contains("Connection")) {
                                f.setAccessible(true);
                                cachedRawConn = f.get(cachedListener);
                                break;
                            }
                        }
                        if (cachedRawConn != null) break;
                    }

                    // Cache keepalive fields on the listener
                    for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                        for (Field f : cls.getDeclaredFields()) {
                            f.setAccessible(true);
                            String fn = f.getName();
                            if (f.getType() == long.class) {
                                if (fn.contains("keepAlive") || fn.contains("KeepAlive") || fn.contains("lastKeepAlive")) {
                                    keepAliveTimeField = f;
                                }
                            }
                        }
                    }

                    // Cache fields on raw connection
                    if (cachedRawConn != null) {
                        for (Class<?> cls = cachedRawConn.getClass(); cls != null; cls = cls.getSuperclass()) {
                            for (Field f : cls.getDeclaredFields()) {
                                f.setAccessible(true);
                                String fn = f.getName();
                                if (f.getType() == long.class && (fn.contains("lastReceived") || fn.contains("LastReceived"))) {
                                    lastReceivedTimeField = f;
                                }
                                if (f.getType() == int.class && fn.equals("tickCount")) {
                                    tickCountField = f;
                                }
                            }
                        }
                    }
                }
            }

            // Apply keepalive resets every tick
            long now = System.currentTimeMillis();
            if (keepAliveTimeField != null && cachedListener != null) {
                try { keepAliveTimeField.setLong(cachedListener, now); } catch (Exception ignored) {}
            }
            if (lastReceivedTimeField != null && cachedRawConn != null) {
                try { lastReceivedTimeField.setLong(cachedRawConn, now); } catch (Exception ignored) {}
            }
            if (tickCountField != null && cachedRawConn != null) {
                try { tickCountField.setInt(cachedRawConn, 0); } catch (Exception ignored) {}
            }

            // Also reset keepalive fields by scanning ALL long fields on the listener
            // (handles obfuscated field names)
            if (cachedListener != null) {
                for (Class<?> cls = cachedListener.getClass(); cls != null; cls = cls.getSuperclass()) {
                    for (Field f : cls.getDeclaredFields()) {
                        if (f.getType() == long.class) {
                            try {
                                f.setAccessible(true);
                                long val = f.getLong(cachedListener);
                                // If it looks like a timestamp (within ~5min of now), reset it
                                if (val > now - 600_000 && val < now + 60_000 && val != now) {
                                    f.setLong(cachedListener, now);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Player findNearestPlayer() {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(bukkitPlayer)) continue;
            if (!p.getWorld().equals(bukkitPlayer.getWorld())) continue;
            double dist = p.getLocation().distanceSquared(bukkitPlayer.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private void broadcastJoin(String name) {
        Bukkit.broadcastMessage("§e" + name + " joined the game");
    }

    private void broadcastLeave(String name) {
        Bukkit.broadcastMessage("§e" + name + " left the game");
    }
}
