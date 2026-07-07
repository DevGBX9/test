package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Object serverPlayer;
    private Player bukkitPlayer;
    private boolean alive;

    private Method getDeltaMovement;
    private Method setDeltaMovement;
    private Method moveMethod;
    private Method knockbackMethod;
    private Class<?> vec3Class;
    private Object moverTypeSelf;

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
                initNMS();
                NMSHelper.broadcastJoinMessage(name);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' spawned at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
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
            getDeltaMovement = entityCls.getMethod("getDeltaMovement");
            setDeltaMovement = entityCls.getMethod("setDeltaMovement", vec3Class);
            moveMethod = entityCls.getMethod("move", moverCls, vec3Class);
            Class<?> livingCls = Class.forName("net.minecraft.world.entity.LivingEntity");
            knockbackMethod = livingCls.getMethod("knockback", double.class, double.class, double.class);
            for (Object c : moverCls.getEnumConstants()) {
                if (c.toString().equals("SELF")) { moverTypeSelf = c; break; }
            }
            if (moverTypeSelf == null) moverTypeSelf = moverCls.getEnumConstants()[0];
        } catch (Exception ignored) {}
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
        if (!alive || serverPlayer == null || knockbackMethod == null) return;
        try {
            knockbackMethod.invoke(serverPlayer, strength, x, z);
        } catch (Exception ignored) {}
    }

    public void tick() {
        if (!alive || serverPlayer == null || getDeltaMovement == null) return;
        try {
            Object delta = getDeltaMovement.invoke(serverPlayer);
            double dx = (double) delta.getClass().getMethod("x").invoke(delta);
            double dy = (double) delta.getClass().getMethod("y").invoke(delta);
            double dz = (double) delta.getClass().getMethod("z").invoke(delta);

            dy -= 0.08;

            Object newDelta = vec3Class.getConstructor(double.class, double.class, double.class)
                .newInstance(dx, dy, dz);
            setDeltaMovement.invoke(serverPlayer, newDelta);

            moveMethod.invoke(serverPlayer, moverTypeSelf, newDelta);

            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.teleport(bukkitPlayer.getLocation());
                Object resultDelta = getDeltaMovement.invoke(serverPlayer);
                double rdx = (double) resultDelta.getClass().getMethod("x").invoke(resultDelta);
                double rdy = (double) resultDelta.getClass().getMethod("y").invoke(resultDelta);
                double rdz = (double) resultDelta.getClass().getMethod("z").invoke(resultDelta);
                bukkitPlayer.setVelocity(new Vector(rdx, rdy, rdz));
            }
        } catch (Exception ignored) {}
    }
}
