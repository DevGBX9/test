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
    private Method getX, getY, getZ;
    private Field connectionField;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public boolean isAlive() { return alive; }
    public Player getBukkitPlayer() { return bukkitPlayer; }

    public void spawn(Location location) {
        if (alive) return;
        if (!NMSHelper.isAvailable()) {
            Bukkit.getLogger().severe("[Mineflayer] Cannot spawn bot - NMS unavailable");
            return;
        }
        try {
            serverPlayer = NMSHelper.createAndJoinFakePlayer(name, uuid, location);
            bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
            bukkitPlayer.teleport(location);

            // Retry world registration after teleport (chunk now loaded)
            NMSHelper.registerEntityInWorld(serverPlayer, location.getWorld());
            // Broadcast bot to all online players for visibility
            NMSHelper.broadcastBotSpawn(serverPlayer);

            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            serverPlayerTick = entityCls.getMethod("tick");
            getX = entityCls.getMethod("getX");
            getY = entityCls.getMethod("getY");
            getZ = entityCls.getMethod("getZ");

            connectionField = null;
            for (Field f : serverPlayer.getClass().getDeclaredFields()) {
                Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                if (f.getType().isAssignableFrom(listenerCls)) {
                    f.setAccessible(true);
                    connectionField = f;
                    break;
                }
            }

            NMSHelper.broadcastJoinMessage(name);
            Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned at "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
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
        NMSHelper.broadcastLeaveMessage(name);
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
            // Call native ServerPlayer.tick() which handles:
            // gravity, collision, friction, knockback, potion effects
            serverPlayerTick.invoke(serverPlayer);

            // Prevent connection timeout disconnect
            if (connectionField != null) {
                Object listener = connectionField.get(serverPlayer);
                if (listener != null) {
                    // Navigate: ServerGamePacketListenerImpl -> Connection
                    try {
                        Field rawConnField = null;
                        for (Class<?> cls = listener.getClass(); cls != null; cls = cls.getSuperclass()) {
                            try {
                                rawConnField = cls.getDeclaredField("connection");
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                        if (rawConnField == null) throw new NoSuchFieldException("connection");
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
                    } catch (Exception ignored) {}
                }
            }

            // Sync position back to Bukkit player after native movement
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                double nx = (double) getX.invoke(serverPlayer);
                double ny = (double) getY.invoke(serverPlayer);
                double nz = (double) getZ.invoke(serverPlayer);
                Location loc = bukkitPlayer.getLocation();
                if (loc.getX() != nx || loc.getY() != ny || loc.getZ() != nz) {
                    bukkitPlayer.teleport(new Location(bukkitPlayer.getWorld(), nx, ny, nz, loc.getYaw(), loc.getPitch()));
                }
            }
        } catch (Exception ignored) {}
    }
}
