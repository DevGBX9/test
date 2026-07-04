package devgbx9.mineflayer;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BotNPC {

    private final String name;
    private final UUID uuid;
    private Mannequin entity;

    public BotNPC(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public Mannequin getEntity() {
        return entity;
    }

    public void spawn(Location location) {
        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        profile.getProperties().add(new ProfileProperty("textures", "", ""));

        entity = location.getWorld().spawn(location, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setCustomName(name);
            m.setCustomNameVisible(true);
            m.setImmovable(true);
            m.setAI(false);
            m.setInvulnerable(true);
            m.setPersistent(true);
        });
    }

    public void remove() {
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }
}
