package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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
        double newX = loc.getX() + vx;
        double newY = loc.getY() + vy;
        double newZ = loc.getZ() + vz;

        boolean onGround = false;
        if (vy <= 0) {
            Block below = loc.getWorld().getBlockAt(loc.getBlockX(), (int) Math.floor(newY), loc.getBlockZ());
            if (below.getType().isSolid() && newY - Math.floor(newY) < 0.01) {
                newY = Math.floor(newY) + 0.01;
                onGround = true;
            }
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
