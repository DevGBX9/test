package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Object serverPlayer;
    private Player bukkitPlayer;
    private boolean alive;
    private Player target;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
        this.alive = false;
        this.target = null;
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

    public void setTarget(Player target) {
        this.target = target;
    }

    public Player getTarget() {
        return target;
    }

    public void spawn(Location location, Player source) {
        if (alive) return;

        if (NMSHelper.isAvailable()) {
            try {
                serverPlayer = NMSHelper.createAndJoinFakePlayer(name, uuid, location, source);
                bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
                bukkitPlayer.teleport(location);
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

    public void remove() {
        if (!alive) return;

        target = null;
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
        if (!alive || bukkitPlayer == null || !bukkitPlayer.isOnline()) return;

        faceTarget();
        simulateMovement();
    }

    private void simulateMovement() {
        Vector vel = bukkitPlayer.getVelocity();
        double vx = vel.getX(), vy = vel.getY(), vz = vel.getZ();
        if (Math.abs(vx) < 0.001 && Math.abs(vy) < 0.001 && Math.abs(vz) < 0.001) return;

        vy -= 0.08;
        vx *= 0.98;
        vz *= 0.98;

        Location loc = bukkitPlayer.getLocation().clone();
        double oldX = loc.getX(), oldY = loc.getY(), oldZ = loc.getZ();
        World world = loc.getWorld();
        double newX = oldX + vx;
        double newY = oldY + vy;
        double newZ = oldZ + vz;

        // X axis
        if (Math.abs(vx) > 0.001 && collides(world, newX, oldY, oldZ)) {
            newX = oldX;
            vx = 0;
        }
        // Y axis
        boolean onGround = false;
        if (Math.abs(vy) > 0.001) {
            if (collides(world, newX, newY, oldZ)) {
                if (vy <= 0) {
                    newY = Math.floor(newY) + 1.0;
                    onGround = true;
                } else {
                    newY = oldY;
                }
                vy = 0;
            }
        }
        // Z axis
        if (Math.abs(vz) > 0.001 && collides(world, newX, newY, newZ)) {
            newZ = oldZ;
            vz = 0;
        }

        if (newY < world.getMinHeight()) {
            newY = world.getHighestBlockYAt((int) Math.floor(newX), (int) Math.floor(newZ)) + 1.0;
            onGround = true;
        }

        loc.setX(newX);
        loc.setY(newY);
        loc.setZ(newZ);
        bukkitPlayer.teleport(loc);

        if (onGround) {
            vx *= 0.6;
            vz *= 0.6;
            vy = 0;
        } else {
            vy *= 0.98;
        }

        bukkitPlayer.setVelocity(new Vector(vx, vy, vz));
    }

    private boolean collides(World world, double x, double y, double z) {
        int minBX = (int) Math.floor(x - 0.3);
        int maxBX = (int) Math.floor(x + 0.3);
        int minBY = (int) Math.floor(y);
        int maxBY = (int) Math.floor(y + 1.8);
        int minBZ = (int) Math.floor(z - 0.3);
        int maxBZ = (int) Math.floor(z + 0.3);
        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (world.getBlockAt(bx, by, bz).getType().isSolid()) return true;
                }
            }
        }
        return false;
    }

    private void faceTarget() {
        if (!alive || bukkitPlayer == null || !bukkitPlayer.isOnline()) return;
        if (target == null || !target.isOnline() || !target.getWorld().equals(bukkitPlayer.getWorld())) return;

        Location botLoc = bukkitPlayer.getLocation().clone();
        Location eyeLoc = bukkitPlayer.getEyeLocation();
        Vector dir = target.getEyeLocation().toVector().subtract(eyeLoc.toVector());
        botLoc.setDirection(dir);
        bukkitPlayer.setRotation(botLoc.getYaw(), botLoc.getPitch());
    }
}
