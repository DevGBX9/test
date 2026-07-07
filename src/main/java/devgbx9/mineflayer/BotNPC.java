package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Object serverPlayer;
    private Player bukkitPlayer;
    private boolean alive;

    private Method entityMove;
    private Field deltaMovement;
    private Class<?> vec3Class;
    private Object moverTypeSelf;
    private boolean nmsReady = false;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isAlive() {
        return alive;
    }

    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }

    public void spawn(Location location) {
        if (alive) return;

        if (NMSHelper.isAvailable()) {
            try {
                serverPlayer = NMSHelper.createAndJoinFakePlayer(name, uuid, location);
                bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
                bukkitPlayer.teleport(location);
                NMSHelper.broadcastJoinMessage(name);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
                initNMS();
                alive = true;
                return;
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] spawn failed: " + e.getMessage());
            }
        }

        Bukkit.getLogger().severe("[Mineflayer] Cannot spawn bot - NMS unavailable");
    }

    private void initNMS() {
        try {
            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> moverCls = Class.forName("net.minecraft.world.entity.MoverType");
            entityMove = entityCls.getMethod("move", moverCls, vec3Class);
            deltaMovement = entityCls.getDeclaredField("deltaMovement");
            deltaMovement.setAccessible(true);
            for (Object c : moverCls.getEnumConstants()) {
                if (c.toString().equals("SELF")) { moverTypeSelf = c; break; }
            }
            if (moverTypeSelf == null) moverTypeSelf = moverCls.getEnumConstants()[0];
            nmsReady = true;
        } catch (Exception e) {
            nmsReady = false;
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

    public void tick() {
        if (!alive || serverPlayer == null) return;

        if (nmsReady) {
            try {
                // 1. Get current NMS velocity (Vec3)
                Object delta = deltaMovement.get(serverPlayer);
                double dx = (double) delta.getClass().getMethod("x").invoke(delta);
                double dy = (double) delta.getClass().getMethod("y").invoke(delta);
                double dz = (double) delta.getClass().getMethod("z").invoke(delta);

                // 2. Apply vanilla gravity
                dy -= 0.08;

                // 3. Create new Vec3 with gravity
                Object newDelta = vec3Class.getConstructor(double.class, double.class, double.class)
                    .newInstance(dx, dy, dz);
                deltaMovement.set(serverPlayer, newDelta);

                // 4. Call the game's own move() — handles collision, friction, position update
                entityMove.invoke(serverPlayer, moverTypeSelf, newDelta);

                // 5. Sync position to all nearby players
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.teleport(bukkitPlayer.getLocation());
                    // Re-sync velocity after teleport (teleport may reset it)
                    Object resultDelta = deltaMovement.get(serverPlayer);
                    double rdx = (double) resultDelta.getClass().getMethod("x").invoke(resultDelta);
                    double rdy = (double) resultDelta.getClass().getMethod("y").invoke(resultDelta);
                    double rdz = (double) resultDelta.getClass().getMethod("z").invoke(resultDelta);
                    bukkitPlayer.setVelocity(new Vector(rdx, rdy, rdz));
                }
                return;
            } catch (Exception ignored) {}
        }

        // Fallback: manual gravity + teleport
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            Vector vel = bukkitPlayer.getVelocity();
            vel.setY(vel.getY() - 0.08);
            Location loc = bukkitPlayer.getLocation().add(vel);
            loc.setYaw(bukkitPlayer.getLocation().getYaw());
            loc.setPitch(bukkitPlayer.getLocation().getPitch());
            bukkitPlayer.teleport(loc);
            bukkitPlayer.setVelocity(vel);
        }
    }
}
