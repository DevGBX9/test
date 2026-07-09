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

    private Method serverPlayerTick;
    private Field connectionField;

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

            NMSHelper.registerEntityInWorld(serverPlayer, location.getWorld());
            NMSHelper.broadcastBotSpawn(serverPlayer);

            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            serverPlayerTick = entityCls.getMethod("tick");

            connectionField = null;
            for (Field f : serverPlayer.getClass().getDeclaredFields()) {
                Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                if (f.getType().isAssignableFrom(listenerCls)) {
                    f.setAccessible(true);
                    connectionField = f;
                    break;
                }
            }

            broadcastJoin(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned");
            alive = true;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] spawn failed: " + e.getMessage());
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

    public void nativeKnockback(double strength, double x, double z) {
        if (!alive || serverPlayer == null) return;
        NMSHelper.nativeKnockback(serverPlayer, strength, x, z);
    }

    public void tick() {
        if (!alive || serverPlayer == null) return;
        try {
            serverPlayerTick.invoke(serverPlayer);

            if (connectionField != null) {
                Object listener = connectionField.get(serverPlayer);
                if (listener != null) {
                    try {
                        Field rawConnField = null;
                        for (Class<?> cls = listener.getClass(); cls != null; cls = cls.getSuperclass()) {
                            try {
                                rawConnField = cls.getDeclaredField("connection");
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                        if (rawConnField != null) {
                            rawConnField.setAccessible(true);
                            Object rawConn = rawConnField.get(listener);
                            if (rawConn != null) {
                                for (Class<?> cls = rawConn.getClass(); cls != null; cls = cls.getSuperclass()) {
                                    try {
                                        Field f = cls.getDeclaredField("lastReceivedTime");
                                        f.setAccessible(true);
                                        f.setLong(rawConn, System.currentTimeMillis());
                                    } catch (NoSuchFieldException ignored) {}
                                    try {
                                        Field f = cls.getDeclaredField("tickCount");
                                        f.setAccessible(true);
                                        f.setInt(rawConn, 0);
                                    } catch (NoSuchFieldException ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                Player nearest = findNearestPlayer();
                if (nearest != null) {
                    Location botLoc = bukkitPlayer.getLocation();
                    Location targetLoc = nearest.getEyeLocation();
                    double dx = targetLoc.getX() - botLoc.getX();
                    double dy = targetLoc.getY() - botLoc.getY();
                    double dz = targetLoc.getZ() - botLoc.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);
                    if (dist > 0.001) {
                        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        if (pitch > 90) pitch = 90;
                        if (pitch < -90) pitch = -90;
                        NMSHelper.setRotation(serverPlayer, yaw, pitch);
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
