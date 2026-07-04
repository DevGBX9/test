package devgbx9.mineflayer;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
            m.addScoreboardTag("mineflayer_bot");
        });

        if (NMSHelper.isAvailable()) {
            try {
                serverPlayer = NMSHelper.createAndJoinFakePlayer(name, uuid, location);
                Player bukkitPlayer = NMSHelper.toBukkitPlayer(serverPlayer);
                bukkitPlayer.setInvulnerable(true);
                NMSHelper.broadcastJoinMessage(name);
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
            NMSHelper.broadcastLeaveMessage(name);
            serverPlayer = null;
        }

        alive = false;
    }

    private ResolvableProfile buildProfile() {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            try {
                return buildProfileFromOnlinePlayer(online);
            } catch (Exception ignored) {}
        }
        UUID profileUuid = online != null ? online.getUniqueId() :
            Bukkit.getOfflinePlayer(name).getUniqueId();
        return ResolvableProfile.resolvableProfile()
                .name(name)
                .uuid(profileUuid)
                .build();
    }

    private ResolvableProfile buildProfileFromOnlinePlayer(Player online) throws Exception {
        Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
        Constructor<?> gpCon = gpClass.getConstructor(UUID.class, String.class);
        Object botGp = gpCon.newInstance(uuid, name);

        Object craftPlayer = online.getClass().getMethod("getHandle").invoke(online);
        Object realGp = null;
        for (Class<?> cls = craftPlayer.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField("gameProfile");
                f.setAccessible(true);
                realGp = f.get(craftPlayer);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (realGp != null) {
            Method getProps = gpClass.getMethod("getProperties");
            Object realProps = getProps.invoke(realGp);
            Object botProps = getProps.invoke(botGp);
            realProps.getClass().getMethod("putAll", java.util.Map.class).invoke(botProps, realProps);
        }

        Method factory = ResolvableProfile.class.getMethod("resolvableProfile", gpClass);
        return (ResolvableProfile) factory.invoke(null, botGp);
    }
}
