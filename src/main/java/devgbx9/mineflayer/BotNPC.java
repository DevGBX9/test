package devgbx9.mineflayer;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Mannequin mannequin;
    private Object serverPlayer;
    private boolean alive;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
        this.alive = false;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    @Nullable
    public Mannequin getMannequin() {
        return mannequin;
    }

    public boolean isAlive() {
        return alive;
    }

    public void spawn(Location location) {
        if (alive) return;

        ResolvableProfile profile = buildProfile();

        mannequin = location.getWorld().spawn(location, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setCustomName(name);
            m.setCustomNameVisible(true);
            m.setImmovable(true);
            m.setAI(false);
            m.setInvulnerable(true);
            m.setPersistent(true);
        });

        if (NMSHelper.isAvailable()) {
            try {
                serverPlayer = NMSHelper.createAndJoinFakePlayer(name, uuid, location);
                Player bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
                bukkitPlayer.setInvulnerable(true);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' registered as player");
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] Player registration failed for '" + name + "': " + e.getMessage());
            }
        }

        alive = true;
    }

    public void remove() {
        if (!alive) return;

        if (mannequin != null) {
            mannequin.remove();
            mannequin = null;
        }

        if (serverPlayer != null) {
            try {
                Player bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
                bukkitPlayer.kickPlayer("Bot removed");
            } catch (Exception ignored) {}
            try {
                NMSHelper.removeFakePlayer(serverPlayer);
            } catch (Exception ignored) {}
            serverPlayer = null;
        }

        alive = false;
    }

    private ResolvableProfile buildProfile() {
        Player online = Bukkit.getPlayerExact(name);
        UUID profileUuid = online != null ? online.getUniqueId() : uuid;
        return ResolvableProfile.resolvableProfile()
                .name(name)
                .uuid(profileUuid)
                .build();
    }
}
