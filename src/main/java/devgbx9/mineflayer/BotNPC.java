package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
                bukkitPlayer.setMaximumNoDamageTicks(0);
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

    public void attackTarget() {
        if (!alive || bukkitPlayer == null || !bukkitPlayer.isOnline()) return;
        if (target == null || !target.isOnline() || !target.getWorld().equals(bukkitPlayer.getWorld())) return;

        double dist = bukkitPlayer.getLocation().distance(target.getLocation());
        if (dist <= 5) {
            bukkitPlayer.attack(target);
            bukkitPlayer.lookAt(target.getEyeLocation());
        }
    }
}
